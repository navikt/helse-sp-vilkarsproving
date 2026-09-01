-- Renamer `kravkilde` til `vurderingskilde` i `opptjeningsvurdering`. Kolonnen forteller hvor en
-- opptjeningsvurdering kommer fra (vurdert av oss / overført fra Infotrygd), og "vurderingskilde" er et
-- mer treffende navn på det enn "kravkilde", som var en rest fra da `Krav`-typen fantes.
ALTER TABLE opptjeningsvurdering RENAME COLUMN kravkilde TO vurderingskilde;
