-- Fjernes etter at både dev og prod har fått baseline V3. En vanlig migrering kommer for sent:
-- Flyway validerer de gamle sjekksummene før den kjører nye migreringer.
DO
$$
DECLARE
    forventet_historikk CONSTANT JSONB := '[
        ["1", "V1__vilkarsproving.sql", -1360214883],
        ["2", "V2__opphav_paa_vilkarsvurdering.sql", 988554288],
        ["3", "V3__krav_over_vilkaar.sql", -1662022934],
        ["4", "V4__gir_rett_til_sykepenger_istedet_for_utfall.sql", 1006120468],
        ["5", "V5__opptjeningsproving_uten_krav.sql", 1898685731],
        ["6", "V6__opptjeningsvurdering_uten_krav.sql", -776398423],
        ["7", "V7__vurderingskilde.sql", -1613648778],
        ["8", "V8__opprydding_dev_tilganger.sql", 1023839664],
        ["9", "V9__rett_til_sykepenger_til_opptjening_ok.sql", -984685569],
        ["10", "V10__vilkarsvurdering_kan_gjenbrukes.sql", -395337431],
        ["11", "V11__normaliser_yrkesaktiv_foer_foreldrepenger_vilkarskode.sql", -2084431493],
        ["12", "V12__opptjeningsvurdering_avgjorende_vilkarskode.sql", 2139053541],
        ["13", "V13__outbox.sql", 1967003348],
        ["14", "V14__lovreferanse.sql", -470427197],
        ["15", "V15__outbox_manuell_vilkarsvurdering.sql", -1283461902],
        ["16", "V16__fjern_lopenummer_fra_vilkarsvurdering.sql", 578287121],
        ["17", "V17__journalpost_id_paa_vilkarsvurdering.sql", -526637410],
        ["18", "V18__fjern_lopenummer_fra_opptjeningsvurdering.sql", -1420413646],
        ["19", "V19__journalpost_id_inn_i_kilde_json.sql", -1569157258],
        ["20", "V20__kategori_paa_opptjeningsvurdering.sql", -45907844],
        ["21", "V21__kategori_paa_opptjeningsproving.sql", 1579554453]
    ]';
    historikk JSONB;
    skjema TEXT;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RETURN;
    END IF;

    PERFORM set_config('lock_timeout', '10s', true);
    -- Låsen tas før historikken leses, også når en annen pod allerede har konvertert den.
    LOCK TABLE public.flyway_schema_history IN ACCESS EXCLUSIVE MODE;

    SELECT jsonb_agg(jsonb_build_array(version, script, checksum) ORDER BY installed_rank)
    INTO historikk
    FROM public.flyway_schema_history;

    IF historikk IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT FROM public.flyway_schema_history
        WHERE installed_rank = 1 AND success
          AND (
            (version = '1' AND type = 'SQL' AND script = 'V1__initielt_skjema.sql')
            OR (version = '3' AND type = 'BASELINE' AND description = 'Squash V1-V21'
                AND script = 'Squash V1-V21' AND checksum IS NULL)
          )
    ) THEN
        IF EXISTS (
            SELECT FROM public.flyway_schema_history h
            WHERE NOT success
               OR (type <> 'SQL' AND NOT (installed_rank = 1 AND type = 'BASELINE'))
               OR EXISTS (
                   SELECT FROM jsonb_array_elements(forventet_historikk) gammel
                   WHERE h.script = gammel ->> 1
               )
        ) THEN
            RAISE EXCEPTION 'Kan ikke squashe: blandet eller feilet Flyway-historikk. Undersøk databasen før ny deploy.';
        END IF;
        RETURN;
    END IF;

    IF historikk <> forventet_historikk THEN
        RAISE EXCEPTION 'Kan ikke squashe: forventet uendret V1-V21. Kontroller versjoner og sjekksummer før ny deploy.';
    END IF;

    IF EXISTS (
        SELECT FROM public.flyway_schema_history
        WHERE NOT success OR type <> 'SQL' OR installed_rank <> version::integer
    ) THEN
        RAISE EXCEPTION 'Kan ikke squashe: V1-V21 må være vellykkede SQL-migreringer i opprinnelig rekkefølge.';
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

    IF skjema IS DISTINCT FROM '981111f294ca93ee9c68a274040dfeee' THEN
        RAISE EXCEPTION 'Kan ikke squashe: skjemaet avviker fra V21 (fingeravtrykk %). Undersøk skjemaet før ny deploy.', skjema;
    END IF;

    DELETE FROM public.flyway_schema_history;
    INSERT INTO public.flyway_schema_history
        (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
    VALUES (1, '3', 'Squash V1-V21', 'BASELINE', 'Squash V1-V21', NULL, current_user, 0, true);

    RAISE NOTICE 'Flyway V1-V21 er erstattet med baseline V3. Applikasjonsdata og skjema er uendret.';
END
$$;
