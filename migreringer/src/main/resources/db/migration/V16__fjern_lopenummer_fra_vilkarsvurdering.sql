-- Vilkårsvurderinger har ingen egen rekkefølge. Opptjening støtter ett vilkår,
-- og koblingstabellen brukes kun for å kunne gjenbruke en vurdering.
DROP INDEX idx_opptjeningsvurdering_vilkarsvurdering_noekkel;

ALTER TABLE vilkarsvurdering
    DROP CONSTRAINT vilkarsvurdering_pkey,
    DROP COLUMN løpenummer;

ALTER TABLE opptjeningsvurdering_vilkarsvurdering
    DROP CONSTRAINT opptjeningsvurdering_vilkarsvurdering_pkey,
    DROP COLUMN løpenummer;
