ALTER TABLE kravvurdering DROP COLUMN utfall;
ALTER TABLE kravvurdering ADD COLUMN rett_til_sykepenger BOOLEAN NOT NULL DEFAULT false;
