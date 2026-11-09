CREATE TABLE opptjeningsvurdering_rettighetskategori
(
    opptjeningsvurdering_id UUID        NOT NULL REFERENCES opptjeningsvurdering (id),
    rettighetskategori_id   UUID        NOT NULL,
    registrert              TIMESTAMPTZ NOT NULL,
    UNIQUE (opptjeningsvurdering_id, rettighetskategori_id)
);

CREATE INDEX idx_opptjeningsvurdering_rettighetskategori_rettighetskategori_id
    ON opptjeningsvurdering_rettighetskategori (rettighetskategori_id);

ALTER TABLE opptjeningsproving
    ADD COLUMN rettighetskategori_ider UUID[] NOT NULL DEFAULT '{}';
