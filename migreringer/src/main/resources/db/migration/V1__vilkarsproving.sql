-- Prosessen som leder fram til en vurdering. Én rad per prøving, oppdateres etter hvert som
-- tilstanden endrer seg.
CREATE TABLE vilkarsproving
(
    løpenummer          BIGSERIAL PRIMARY KEY,
    id                  UUID        NOT NULL UNIQUE,
    vilkår              TEXT        NOT NULL,
    fødselsnummer       VARCHAR(11) NOT NULL,
    skjæringstidspunkt  DATE        NOT NULL,
    startet             TIMESTAMPTZ NOT NULL,
    tilstand            TEXT        NOT NULL,
    utestående_behov    TEXT,
    vurdering_id        UUID,
    opprettet           TIMESTAMPTZ NOT NULL DEFAULT now(),
    endret              TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Tilstandsfeltene hører sammen: en prøving venter enten på et behov, eller er fullført med en
    -- vurdering. Databasen skal ikke kunne inneholde en tilstand domenet ikke kan uttrykke.
    CONSTRAINT vilkarsproving_tilstand_er_konsistent CHECK (
        (tilstand = 'STARTET' AND utestående_behov IS NULL AND vurdering_id IS NULL) OR
        (tilstand = 'VENTER_PÅ_GRUNNLAG' AND utestående_behov IS NOT NULL AND vurdering_id IS NULL) OR
        (tilstand = 'FULLFØRT' AND utestående_behov IS NULL AND vurdering_id IS NOT NULL)
        )
);

-- Invarianten "kun én aktiv prøving per (vilkår, fødselsnummer, skjæringstidspunkt)" håndheves her,
-- ikke av en sjekk i applikasjonskoden. Vilkåret er en del av nøkkelen, slik at en pågående
-- opptjeningsprøving ikke hindrer at medlemskap prøves samtidig for samme person og skjæringstidspunkt.
CREATE UNIQUE INDEX uix_vilkarsproving_aktiv
    ON vilkarsproving (vilkår, fødselsnummer, skjæringstidspunkt)
    WHERE tilstand <> 'FULLFØRT';

-- Oppslag av siste prøving for en nøkkel.
CREATE INDEX idx_vilkarsproving_noekkel
    ON vilkarsproving (vilkår, fødselsnummer, skjæringstidspunkt, løpenummer DESC);

-- Ferdige vurderinger. Skrives kun én gang og oppdateres aldri; en ny prøving gir en ny rad,
-- slik at historikken består.
CREATE TABLE vilkarsvurdering
(
    løpenummer          BIGSERIAL PRIMARY KEY,
    id                  UUID        NOT NULL UNIQUE,
    -- Prøvingen som produserte vurderingen. Motsatt retning (vilkarsproving.vurdering_id) har
    -- bevisst ingen fremmednøkkel: en prøving som fullføres umiddelbart skrives før vurderingen.
    prøving_id          UUID        REFERENCES vilkarsproving (id),
    vilkår              TEXT        NOT NULL,
    fødselsnummer       VARCHAR(11) NOT NULL,
    skjæringstidspunkt  DATE        NOT NULL,
    grunnlag            JSONB       NOT NULL,
    kodeverkkode        TEXT        NOT NULL,
    kilde               JSONB       NOT NULL,
    vurdert_tidspunkt   TIMESTAMPTZ NOT NULL,
    opprettet           TIMESTAMPTZ NOT NULL DEFAULT now(),
    teknisk_notat       TEXT                 DEFAULT NULL
);

-- Oppslag av gjeldende vurdering for en nøkkel.
CREATE INDEX idx_vilkarsvurdering_noekkel
    ON vilkarsvurdering (vilkår, fødselsnummer, skjæringstidspunkt, løpenummer DESC);

CREATE INDEX idx_vilkarsvurdering_proving_id
    ON vilkarsvurdering (prøving_id);
