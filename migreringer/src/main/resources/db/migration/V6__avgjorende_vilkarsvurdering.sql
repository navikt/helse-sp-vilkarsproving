ALTER TABLE opptjeningsvurdering
    ADD COLUMN avgjørende_vilkårsvurdering UUID;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM opptjeningsvurdering o
        WHERE o.avgjørende_vilkårskode IS NOT NULL
          AND (
              SELECT count(*)
              FROM opptjeningsvurdering_vilkarsvurdering ov
              JOIN vilkarsvurdering v ON v.id = ov.vilkarsvurdering_id
              WHERE ov.opptjeningsvurdering_id = o.id
                AND v.vilkårskode = o.avgjørende_vilkårskode
          ) <> 1
    ) THEN
        RAISE EXCEPTION 'Kan ikke migrere avgjørende_vilkårskode: forventer nøyaktig én tilknyttet vilkårsvurdering per kode';
    END IF;
END $$;

UPDATE opptjeningsvurdering o
SET avgjørende_vilkårsvurdering = v.id
FROM opptjeningsvurdering_vilkarsvurdering ov
JOIN vilkarsvurdering v ON v.id = ov.vilkarsvurdering_id
WHERE ov.opptjeningsvurdering_id = o.id
  AND v.vilkårskode = o.avgjørende_vilkårskode;

ALTER TABLE opptjeningsvurdering
    ADD CONSTRAINT fk_opptjeningsvurdering_avgjorende_vilkarsvurdering
        FOREIGN KEY (avgjørende_vilkårsvurdering) REFERENCES vilkarsvurdering (id);
