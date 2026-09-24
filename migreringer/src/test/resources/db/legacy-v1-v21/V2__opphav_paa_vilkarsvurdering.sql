-- Grunnlaget hører til opphavet, ikke til vurderingen.
--
-- Et grunnlag finnes bare når vi selv har vurdert maskinelt. Vurderinger gjort av en saksbehandler,
-- eller overført fra Infotrygd, har ingen grunnlagsdata. Med grunnlaget i egen kolonne kunne
-- databasen uttrykke kombinasjoner domenet ikke har (et grunnlag på en manuell vurdering); ved å
-- flytte det inn i opphavs-json-en er den muligheten borte.

ALTER TABLE vilkarsvurdering
    ADD COLUMN opphav JSONB;


-- Ingen rad skal kunne ha et opphav vi ikke klarte å tolke. Feiler dette, står det en kildetype i
-- databasen som denne migreringen ikke kjenner, og da skal vi stoppe – ikke gjette.
ALTER TABLE vilkarsvurdering
    ALTER COLUMN opphav SET NOT NULL;

ALTER TABLE vilkarsvurdering
    DROP COLUMN kilde,
    DROP COLUMN grunnlag;
