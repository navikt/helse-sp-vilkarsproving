-- Rollen opprettes av Nais i dev, men finnes ikke i prod eller lokale databaser.
DO
$$
    BEGIN
        IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sp-vilkarsproving-opprydding-dev')
        THEN
            GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "sp-vilkarsproving-opprydding-dev";
            GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "sp-vilkarsproving-opprydding-dev";
            ALTER DEFAULT PRIVILEGES FOR USER "sp-vilkarsproving" IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO "sp-vilkarsproving-opprydding-dev";
            ALTER DEFAULT PRIVILEGES FOR USER "sp-vilkarsproving" IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO "sp-vilkarsproving-opprydding-dev";
        END IF;
    END
$$;
