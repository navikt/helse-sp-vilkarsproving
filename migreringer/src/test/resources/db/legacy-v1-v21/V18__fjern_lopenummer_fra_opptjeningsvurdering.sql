ALTER TABLE opptjeningsvurdering
    ADD COLUMN vurdert_tidspunkt TIMESTAMPTZ;

UPDATE opptjeningsvurdering ov
SET vurdert_tidspunkt = COALESCE(
    (
        SELECT MAX(v.vurdert_tidspunkt)
        FROM opptjeningsvurdering_vilkarsvurdering ovv
        JOIN vilkarsvurdering v ON v.id = ovv.vilkarsvurdering_id
        WHERE ovv.opptjeningsvurdering_id = ov.id
    ),
    ov.opprettet
);

ALTER TABLE opptjeningsvurdering
    ALTER COLUMN vurdert_tidspunkt SET NOT NULL;

DROP INDEX idx_opptjeningsvurdering_noekkel;

ALTER TABLE opptjeningsvurdering
    DROP CONSTRAINT opptjeningsvurdering_pkey,
    DROP COLUMN løpenummer;

CREATE INDEX idx_opptjeningsvurdering_noekkel
    ON opptjeningsvurdering (fødselsnummer, skjæringstidspunkt, vurdert_tidspunkt DESC, opprettet DESC);
