CREATE TABLE opptjeningsproving
(
    løpenummer             BIGSERIAL PRIMARY KEY,
    id                     UUID        NOT NULL UNIQUE,
    fødselsnummer          VARCHAR(11) NOT NULL,
    skjæringstidspunkt     DATE        NOT NULL,
    startet                TIMESTAMPTZ NOT NULL,
    tilstand               TEXT        NOT NULL,
    utestående_behov       TEXT,
    opptjeningsvurdering_id UUID,
    opprettet              TIMESTAMPTZ NOT NULL DEFAULT now(),
    endret                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    kategori               TEXT        NOT NULL,

    CONSTRAINT opptjeningsproving_tilstand_er_konsistent CHECK (
        (tilstand = 'STARTET' AND utestående_behov IS NULL AND opptjeningsvurdering_id IS NULL) OR
        (tilstand = 'VENTER_PÅ_GRUNNLAG' AND utestående_behov IS NOT NULL AND opptjeningsvurdering_id IS NULL) OR
        (tilstand = 'FULLFØRT' AND utestående_behov IS NULL AND opptjeningsvurdering_id IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uix_opptjeningsproving_aktiv
    ON opptjeningsproving (fødselsnummer, skjæringstidspunkt, kategori)
    WHERE tilstand <> 'FULLFØRT';

CREATE INDEX idx_opptjeningsproving_noekkel
    ON opptjeningsproving (fødselsnummer, skjæringstidspunkt, løpenummer DESC);

CREATE TABLE opptjeningsvurdering
(
    id                     UUID        NOT NULL UNIQUE,
    fødselsnummer          VARCHAR(11) NOT NULL,
    skjæringstidspunkt     DATE        NOT NULL,
    vurderingskilde        TEXT        NOT NULL,
    opprettet              TIMESTAMPTZ NOT NULL DEFAULT now(),
    opptjening_ok          BOOLEAN     NOT NULL DEFAULT false,
    avgjørende_vilkårskode TEXT,
    vurdert_tidspunkt      TIMESTAMPTZ NOT NULL,
    kategori               TEXT        NOT NULL
);

CREATE INDEX idx_opptjeningsvurdering_noekkel
    ON opptjeningsvurdering (fødselsnummer, skjæringstidspunkt, vurdert_tidspunkt DESC, opprettet DESC);

CREATE TABLE vilkarsvurdering
(
    id                UUID        NOT NULL UNIQUE,
    vilkårskode       TEXT        NOT NULL,
    utfall            TEXT        NOT NULL,
    vurdert_tidspunkt TIMESTAMPTZ,
    kilde             JSONB       NOT NULL,
    opprettet         TIMESTAMPTZ NOT NULL DEFAULT now(),
    lovreferanse      JSONB
);

CREATE TABLE opptjeningsvurdering_vilkarsvurdering
(
    opptjeningsvurdering_id UUID NOT NULL REFERENCES opptjeningsvurdering (id),
    vilkarsvurdering_id     UUID NOT NULL REFERENCES vilkarsvurdering (id),
    UNIQUE (opptjeningsvurdering_id, vilkarsvurdering_id),
    opprettet              TIMESTAMP DEFAULT now()
);
