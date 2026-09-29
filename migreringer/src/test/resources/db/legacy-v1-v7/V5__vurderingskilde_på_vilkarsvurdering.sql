ALTER TABLE vilkarsvurdering
    ADD COLUMN vurderingskilde TEXT;

UPDATE vilkarsvurdering v
SET vurderingskilde = 'VURDERT_I_SP_VILKARSPROVING';

ALTER TABLE vilkarsvurdering
    ALTER COLUMN vurderingskilde SET NOT NULL;
