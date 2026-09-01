-- V6 følger opp V5 på samme måte for kravvurdering: det finnes bare ett krav (opptjening), så
-- kravvurdering er ikke en generisk vurderingstabell på tvers av krav, men opptjeningsvurderingen selv.
-- Ingenting er i produksjon, så vi endrer skjemaet direkte i stedet for å migrere data.

ALTER TABLE kravvurdering RENAME TO opptjeningsvurdering;

ALTER TABLE opptjeningsvurdering RENAME CONSTRAINT kravvurdering_pkey TO opptjeningsvurdering_pkey;
ALTER TABLE opptjeningsvurdering RENAME CONSTRAINT kravvurdering_id_key TO opptjeningsvurdering_id_key;
ALTER SEQUENCE kravvurdering_løpenummer_seq RENAME TO opptjeningsvurdering_løpenummer_seq;

DROP INDEX idx_kravvurdering_noekkel;

-- `krav` var bare med for å skille flere kravtyper fra hverandre. Domenet har ingen flere krav enn
-- opptjening, så kolonnen og nøkkelen som inneholdt den, bar ingen informasjon.
ALTER TABLE opptjeningsvurdering DROP COLUMN krav;

-- Oppslag av gjeldende opptjeningsvurdering for en nøkkel.
CREATE INDEX idx_opptjeningsvurdering_noekkel
    ON opptjeningsvurdering (fødselsnummer, skjæringstidspunkt, løpenummer DESC);

-- `kravvurdering_id` het slik fordi den pekte til `kravvurdering`. Nå som den tabellen heter
-- `opptjeningsvurdering`, følger kolonnenavnet — og nøkler/fremmednøkler som bærer det — med i begge
-- tabellene som refererer til den.
ALTER TABLE vilkarsvurdering RENAME COLUMN kravvurdering_id TO opptjeningsvurdering_id;
ALTER TABLE vilkarsvurdering
    RENAME CONSTRAINT vilkarsvurdering_kravvurdering_id_fkey TO vilkarsvurdering_opptjeningsvurdering_id_fkey;
ALTER INDEX idx_vilkarsvurdering_kravvurdering_id RENAME TO idx_vilkarsvurdering_opptjeningsvurdering_id;

ALTER TABLE opptjeningsproving RENAME COLUMN kravvurdering_id TO opptjeningsvurdering_id;
