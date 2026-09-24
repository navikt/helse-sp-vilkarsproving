ALTER TABLE opptjeningsvurdering
    ADD COLUMN kategori TEXT;

UPDATE opptjeningsvurdering ov
SET kategori = 'SELVSTENDIG_NÆRINGSDRIVENDE'
WHERE EXISTS (
    SELECT 1
    FROM opptjeningsvurdering_vilkarsvurdering ovv
    JOIN vilkarsvurdering v ON v.id = ovv.vilkarsvurdering_id
    WHERE ovv.opptjeningsvurdering_id = ov.id
      AND v.kilde -> 'grunnlag' ->> 'type' = 'SELVSTENDIG_NÆRINGSDRIVENDE'
);

UPDATE opptjeningsvurdering ov
SET kategori = (
    SELECT tidligere.kategori
    FROM opptjeningsvurdering tidligere
    WHERE tidligere.fødselsnummer = ov.fødselsnummer
      AND tidligere.skjæringstidspunkt = ov.skjæringstidspunkt
      AND tidligere.kategori IS NOT NULL
      AND tidligere.opprettet < ov.opprettet
    ORDER BY tidligere.vurdert_tidspunkt DESC, tidligere.opprettet DESC
    LIMIT 1
)
WHERE ov.kategori IS NULL
  AND EXISTS (
      SELECT 1
      FROM opptjeningsvurdering_vilkarsvurdering ovv
      JOIN vilkarsvurdering v ON v.id = ovv.vilkarsvurdering_id
      WHERE ovv.opptjeningsvurdering_id = ov.id
        AND v.kilde ->> 'type' = 'SAKSBEHANDLER'
  );

UPDATE opptjeningsvurdering
SET kategori = 'ARBEIDSTAKER'
WHERE kategori IS NULL;

ALTER TABLE opptjeningsvurdering
    ALTER COLUMN kategori SET NOT NULL;
