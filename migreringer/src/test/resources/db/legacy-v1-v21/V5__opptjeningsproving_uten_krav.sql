-- V5 følger opp utflatingen av Krav-typen i domenet: det finnes bare ett krav (opptjening), så
-- kravproving er ikke en generisk prøvingstabell på tvers av krav, men opptjeningsprøvingen selv.
-- Ingenting er i produksjon, så vi endrer skjemaet direkte i stedet for å migrere data.

ALTER TABLE kravproving RENAME TO opptjeningsproving;

ALTER TABLE opptjeningsproving
    RENAME CONSTRAINT kravproving_tilstand_er_konsistent TO opptjeningsproving_tilstand_er_konsistent;
ALTER TABLE opptjeningsproving RENAME CONSTRAINT kravproving_pkey TO opptjeningsproving_pkey;
ALTER TABLE opptjeningsproving RENAME CONSTRAINT kravproving_id_key TO opptjeningsproving_id_key;
ALTER SEQUENCE kravproving_løpenummer_seq RENAME TO opptjeningsproving_løpenummer_seq;

DROP INDEX uix_kravproving_aktiv;
DROP INDEX idx_kravproving_noekkel;

-- `krav` var bare med for å skille flere kravtyper fra hverandre. Domenet har ingen flere krav enn
-- opptjening, så kolonnen og nøklene som inneholdt den, bar ingen informasjon.
ALTER TABLE opptjeningsproving DROP COLUMN krav;

-- Invarianten "kun én aktiv prøving per (fødselsnummer, skjæringstidspunkt)" håndheves her, ikke av en
-- sjekk i applikasjonskoden.
CREATE UNIQUE INDEX uix_opptjeningsproving_aktiv
    ON opptjeningsproving (fødselsnummer, skjæringstidspunkt)
    WHERE tilstand <> 'FULLFØRT';

-- Oppslag av siste prøving for en nøkkel.
CREATE INDEX idx_opptjeningsproving_noekkel
    ON opptjeningsproving (fødselsnummer, skjæringstidspunkt, løpenummer DESC);
