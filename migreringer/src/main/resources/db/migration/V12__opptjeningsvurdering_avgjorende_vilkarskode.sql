-- avgjørendeVilkårskode utledes i dag (nesten alltid) riktig i domenet, men er ikke en ren funksjon av
-- utfallet alene — den avhenger av selve vurderingen som ble gjort på tidspunktet. Feltet skal derfor
-- vurderes én gang og lagres, ikke utledes på nytt hver gang. Kolonnen er nullable: en
-- OverførtFraInfotrygd-vurdering har aldri noe avgjørende vilkår, og en VurdertISpeil-vurdering kan i
-- prinsippet mangle et avgjørende vilkår hvis ingen regel traff.
ALTER TABLE opptjeningsvurdering ADD COLUMN avgjørende_vilkårskode TEXT NULL;

-- Backfill av eksisterende VurdertISpeil-rader (vurderingskilde <> OVERFOERT_FRA_INFOTRYGD). Dette
-- gjenskaper forretningsregelen fra Vilkårsvurdering.avgjørendeVilkårskode() i SQL, kun for dette
-- engangsformålet — fremtidig utledning skjer utelukkende i domenekoden:
--
-- 1. Hvis hovedregelen (OPPTJENING_ARBEID_MINST_4_UKER) er oppfylt, er den avgjørende.
-- 2. Ellers, hvis hovedregelen finnes (uansett utfall) OG OPPTJENING_LIKESTILT_YTELSE er oppfylt
--    OG OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER finnes blant vilkårsvurderingene, er sistnevnte
--    avgjørende.
-- 3. Ellers er det ingen avgjørende vilkår (NULL).
WITH ledd AS (
    SELECT
        ov.opptjeningsvurdering_id,
        v.vilkårskode,
        v.utfall
    FROM opptjeningsvurdering_vilkarsvurdering ov
    JOIN vilkarsvurdering v ON v.id = ov.vilkarsvurdering_id
),
     avgjort AS (
         SELECT
             opptjeningsvurdering_id,
             CASE
                 WHEN bool_or(vilkårskode = 'OPPTJENING_ARBEID_MINST_4_UKER' AND utfall = 'OPPFYLT')
                     THEN 'OPPTJENING_ARBEID_MINST_4_UKER'
                 WHEN bool_or(vilkårskode = 'OPPTJENING_ARBEID_MINST_4_UKER')
                     AND bool_or(vilkårskode = 'OPPTJENING_LIKESTILT_YTELSE' AND utfall = 'OPPFYLT')
                     AND bool_or(vilkårskode = 'OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER')
                     THEN 'OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER'
                 ELSE NULL
                 END AS avgjørende_vilkårskode
         FROM ledd
         GROUP BY opptjeningsvurdering_id
     )
UPDATE opptjeningsvurdering o
SET avgjørende_vilkårskode = a.avgjørende_vilkårskode
FROM avgjort a
WHERE o.id = a.opptjeningsvurdering_id
  AND o.vurderingskilde <> 'OVERFOERT_FRA_INFOTRYGD';
