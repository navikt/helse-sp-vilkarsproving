WITH tabeller AS (
    SELECT c.oid, c.relname
    FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
), definisjoner AS (
    SELECT jsonb_build_array('kolonne', t.relname, a.attname, format_type(a.atttypid, a.atttypmod),
                            a.attnotnull, pg_get_expr(d.adbin, d.adrelid))::text AS definisjon
    FROM tabeller t JOIN pg_attribute a ON a.attrelid = t.oid
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE a.attnum > 0 AND NOT a.attisdropped
    UNION ALL
    SELECT jsonb_build_array('constraint', t.relname, c.conname, pg_get_constraintdef(c.oid), c.convalidated)::text
    FROM tabeller t JOIN pg_constraint c ON c.conrelid = t.oid
    WHERE c.contype <> 'n'
    UNION ALL
    SELECT jsonb_build_array('indeks', t.relname, pg_get_indexdef(i.indexrelid), i.indisvalid)::text
    FROM tabeller t JOIN pg_index i ON i.indrelid = t.oid
    UNION ALL
    SELECT jsonb_build_array('sekvens', c.relname, s.seqtypid::regtype::text, s.seqstart, s.seqincrement,
                            s.seqmax, s.seqmin, s.seqcache, s.seqcycle)::text
    FROM pg_sequence s JOIN pg_class c ON c.oid = s.seqrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
)
SELECT definisjon FROM definisjoner ORDER BY definisjon COLLATE "C";
