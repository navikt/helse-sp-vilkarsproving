-- Fjernes etter at både dev og prod har fått baseline V3. En vanlig migrering kommer for sent:
-- Flyway validerer de gamle sjekksummene før den kjører nye migreringer.
DO
$$
DECLARE
    gammel_v4_v7 CONSTANT JSONB := '[
        ["4", "SQL", "V4__backfill_og_obligatorisk_lovreferanse.sql", 635595512],
        ["5", "SQL", "V5__vurderingskilde_på_vilkarsvurdering.sql", 341682724],
        ["6", "SQL", "V6__avgjorende_vilkarsvurdering.sql", -1877087141],
        ["7", "SQL", "V7__drop_avgjorende_vilkarskode.sql", -1366936175]
    ]';
    -- Dev og prod ble baselinet på V3 ved forrige squash og har kjørt V4-V7 etterpå.
    forventet_etter_forrige_squash CONSTANT JSONB := '[
        ["3", "BASELINE", "Squash V1-V21", null]
    ]'::jsonb || gammel_v4_v7;
    -- Databaser opprettet etter forrige squash har kjørt de gamle V1-V7 direkte.
    forventet_opprettet_etter_forrige_squash CONSTANT JSONB := '[
        ["1", "SQL", "V1__initielt_skjema.sql", -640090755],
        ["2", "SQL", "V2__outbox.sql", 1967003348],
        ["3", "SQL", "V3__opprydding_dev_tilganger.sql", 695706574]
    ]'::jsonb || gammel_v4_v7;
    ny_v1_sjekksum CONSTANT INTEGER := -1536202;
    historikk JSONB;
    skjema TEXT;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RETURN;
    END IF;

    PERFORM set_config('lock_timeout', '10s', true);
    -- Låsen tas før historikken leses, også når en annen pod allerede har konvertert den.
    LOCK TABLE public.flyway_schema_history IN ACCESS EXCLUSIVE MODE;

    SELECT jsonb_agg(jsonb_build_array(version, type, script, checksum) ORDER BY installed_rank)
    INTO historikk
    FROM public.flyway_schema_history;

    IF historikk IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT FROM public.flyway_schema_history
        WHERE installed_rank = 1 AND success
          AND (
            (version = '1' AND type = 'SQL' AND script = 'V1__initielt_skjema.sql' AND checksum = ny_v1_sjekksum)
            OR (version = '3' AND type = 'BASELINE' AND description = 'Squash V1-V7'
                AND script = 'Squash V1-V7' AND checksum IS NULL)
          )
    ) THEN
        IF EXISTS (
            SELECT FROM public.flyway_schema_history h
            WHERE NOT success
               OR (type <> 'SQL' AND NOT (installed_rank = 1 AND type = 'BASELINE'))
               OR script = 'Squash V1-V21'
               OR EXISTS (
                   SELECT FROM jsonb_array_elements(gammel_v4_v7) gammel
                   WHERE h.script = gammel ->> 2
               )
        ) THEN
            RAISE EXCEPTION 'Kan ikke squashe: blandet eller feilet Flyway-historikk. Undersøk databasen før ny deploy.';
        END IF;
        RETURN;
    END IF;

    IF historikk <> forventet_etter_forrige_squash AND historikk <> forventet_opprettet_etter_forrige_squash THEN
        RAISE EXCEPTION 'Kan ikke squashe: forventet uendret historikk til og med V7. Kontroller versjoner og sjekksummer før ny deploy.';
    END IF;

    IF EXISTS (SELECT FROM public.flyway_schema_history WHERE NOT success) THEN
        RAISE EXCEPTION 'Kan ikke squashe: historikken til og med V7 inneholder feilede migreringer.';
    END IF;

    PERFORM set_config('search_path', 'pg_catalog, public', true);
    -- Sammenligner logiske definisjoner, ikke OID-er, fysisk kolonnerekkefølge eller sekvensverdier.
    WITH tabeller AS (
        SELECT c.oid, c.relname
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind = 'r'
          AND c.relname IN ('opptjeningsproving', 'opptjeningsvurdering', 'vilkarsvurdering',
                            'opptjeningsvurdering_vilkarsvurdering', 'outbox')
    ), definisjoner AS (
        SELECT jsonb_build_array('kolonne', t.relname, a.attname, format_type(a.atttypid, a.atttypmod),
                                a.attnotnull, pg_get_expr(d.adbin, d.adrelid))::text AS definisjon
        FROM tabeller t JOIN pg_attribute a ON a.attrelid = t.oid
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attnum > 0 AND NOT a.attisdropped
        UNION ALL
        SELECT jsonb_build_array('constraint', t.relname, c.conname, pg_get_constraintdef(c.oid), c.convalidated)::text
        FROM tabeller t JOIN pg_constraint c ON c.conrelid = t.oid
        -- NOT NULL er allerede med i kolonnedefinisjonen; automatisk navn avhenger av tabellens navnehistorikk.
        WHERE c.contype <> 'n'
        UNION ALL
        SELECT jsonb_build_array('indeks', t.relname, pg_get_indexdef(i.indexrelid), i.indisvalid)::text
        FROM tabeller t JOIN pg_index i ON i.indrelid = t.oid
        UNION ALL
        SELECT jsonb_build_array('sekvens', c.relname, s.seqtypid::regtype::text, s.seqstart, s.seqincrement,
                                s.seqmax, s.seqmin, s.seqcache, s.seqcycle)::text
        FROM pg_sequence s JOIN pg_class c ON c.oid = s.seqrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = 'opptjeningsproving_løpenummer_seq'
    )
    SELECT md5(string_agg(definisjon, E'\n' ORDER BY definisjon COLLATE "C"))
    INTO skjema
    FROM definisjoner;

    IF skjema IS DISTINCT FROM '87a85dc3821b1d81c6401d57d29d8fed' THEN
        RAISE EXCEPTION 'Kan ikke squashe: skjemaet avviker fra V7 (fingeravtrykk %). Undersøk skjemaet før ny deploy.', skjema;
    END IF;

    DELETE FROM public.flyway_schema_history;
    INSERT INTO public.flyway_schema_history
        (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
    VALUES (1, '3', 'Squash V1-V7', 'BASELINE', 'Squash V1-V7', NULL, current_user, 0, true);

    RAISE NOTICE 'Flyway-historikken til og med V7 er erstattet med baseline V3. Applikasjonsdata og skjema er uendret.';
END
$$;
