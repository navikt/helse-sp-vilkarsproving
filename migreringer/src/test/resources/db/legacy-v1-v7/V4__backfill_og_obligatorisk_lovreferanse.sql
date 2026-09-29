-- Alle eksisterende rader gjelder opptjeningsvilkåret i § 8-2, uavhengig av hvilken
-- vilkårskode-streng de ble lagret med. Backfillen filtrerer derfor ikke på vilkårskode:
-- en gjenværende NULL-rad ville gjort SET NOT NULL under umulig.
UPDATE vilkarsvurdering
SET lovreferanse = jsonb_build_object(
    'lov', 'folketrygdloven',
    'paragraf', '8-2',
    'avsnitt', 1,
    'setning', 1,
    'bokstav', NULL,
    'iKraftFra', '2025-12-22'
)
WHERE lovreferanse IS NULL;

ALTER TABLE vilkarsvurdering
    ALTER COLUMN lovreferanse SET NOT NULL;
