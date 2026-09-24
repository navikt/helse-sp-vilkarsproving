CREATE TABLE opptjeningsvurdering_vilkarsvurdering
(
    løpenummer              BIGSERIAL PRIMARY KEY,
    opptjeningsvurdering_id UUID NOT NULL REFERENCES opptjeningsvurdering (id),
    vilkarsvurdering_id     UUID NOT NULL REFERENCES vilkarsvurdering (id),
    UNIQUE (opptjeningsvurdering_id, vilkarsvurdering_id),
    opprettet               TIMESTAMP DEFAULT now()
);

INSERT INTO opptjeningsvurdering_vilkarsvurdering (opptjeningsvurdering_id, vilkarsvurdering_id)
SELECT opptjeningsvurdering_id, id
FROM vilkarsvurdering
ORDER BY løpenummer;

CREATE INDEX idx_opptjeningsvurdering_vilkarsvurdering_noekkel
    ON opptjeningsvurdering_vilkarsvurdering (opptjeningsvurdering_id, løpenummer);

DROP INDEX idx_vilkarsvurdering_opptjeningsvurdering_id;

ALTER TABLE vilkarsvurdering
    DROP CONSTRAINT vilkarsvurdering_opptjeningsvurdering_id_fkey,
    DROP COLUMN opptjeningsvurdering_id;
