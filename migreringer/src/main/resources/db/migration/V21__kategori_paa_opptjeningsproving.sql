ALTER TABLE opptjeningsproving
    ADD COLUMN kategori TEXT;

UPDATE opptjeningsproving p
SET kategori = v.kategori
FROM opptjeningsvurdering v
WHERE p.opptjeningsvurdering_id = v.id;

UPDATE opptjeningsproving
SET kategori = 'ARBEIDSTAKER'
WHERE kategori IS NULL;

ALTER TABLE opptjeningsproving
    ALTER COLUMN kategori SET NOT NULL;

DROP INDEX uix_opptjeningsproving_aktiv;

CREATE UNIQUE INDEX uix_opptjeningsproving_aktiv
    ON opptjeningsproving (fødselsnummer, skjæringstidspunkt, kategori)
    WHERE tilstand <> 'FULLFØRT';
