-- V3 bygger om tabellene fra vilkår-nivå til krav-nivå. Ingenting er i produksjon, så vi dropper og
-- gjenskaper i stedet for å migrere data.
--
-- To endringer i grunnen:
--
-- 1. En prøving ligger på kravnivå, ikke vilkårsnivå: fireukersvilkåret og et eventuelt unntaksvilkår
--    hører til den samme prøvingen av opptjeningskravet.
-- 2. Vurderingen er nå et aggregat i to tabeller: `kravvurdering` (kravet, avgjort eller overført fra
--    Infotrygd) og `vilkarsvurdering` (stien av vilkår som ble prøvd for å avgjøre det, aldri tom for et
--    krav vurdert av oss).
--
-- `kravkilde` og `vilkårskode`/`kildetype` (inni `kilde`-json-en) er bevisst TEXT, ikke Postgres-enum, og
-- CHECK-en her lister ikke opp lovlige verdier. Domenet håndhever vokabularet; skjemaet begrenser kun
-- formen. En ny kilde — som når data migreres inn fra Spleis — skal være en ren kodeendring, ikke et nytt
-- skjemaoppsett.

DROP TABLE IF EXISTS vilkarsvurdering;
DROP TABLE IF EXISTS vilkarsproving;

CREATE TABLE kravproving
(
    løpenummer          BIGSERIAL PRIMARY KEY,
    id                  UUID        NOT NULL UNIQUE,
    krav                TEXT        NOT NULL,
    fødselsnummer       VARCHAR(11) NOT NULL,
    skjæringstidspunkt  DATE        NOT NULL,
    startet             TIMESTAMPTZ NOT NULL,
    tilstand            TEXT        NOT NULL,
    utestående_behov    TEXT,
    kravvurdering_id    UUID,
    opprettet           TIMESTAMPTZ NOT NULL DEFAULT now(),
    endret              TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Tilstandsfeltene hører sammen: en prøving venter enten på et behov, eller er fullført med en
    -- kravvurdering. Databasen skal ikke kunne inneholde en tilstand domenet ikke kan uttrykke. Dette er
    -- den faste tilstandsmaskinen for prøvingslivssyklusen, ikke en åpen provenans-vokabular, og listes
    -- derfor opp her.
    CONSTRAINT kravproving_tilstand_er_konsistent CHECK (
        (tilstand = 'STARTET' AND utestående_behov IS NULL AND kravvurdering_id IS NULL) OR
        (tilstand = 'VENTER_PÅ_GRUNNLAG' AND utestående_behov IS NOT NULL AND kravvurdering_id IS NULL) OR
        (tilstand = 'FULLFØRT' AND utestående_behov IS NULL AND kravvurdering_id IS NOT NULL)
        )
);

-- Invarianten "kun én aktiv prøving per (krav, fødselsnummer, skjæringstidspunkt)" håndheves her, ikke av
-- en sjekk i applikasjonskoden. Kravet er en del av nøkkelen, slik at en pågående opptjeningsprøving ikke
-- hindrer at medlemskap prøves samtidig for samme person og skjæringstidspunkt.
CREATE UNIQUE INDEX uix_kravproving_aktiv
    ON kravproving (krav, fødselsnummer, skjæringstidspunkt)
    WHERE tilstand <> 'FULLFØRT';

-- Oppslag av siste prøving for en nøkkel.
CREATE INDEX idx_kravproving_noekkel
    ON kravproving (krav, fødselsnummer, skjæringstidspunkt, løpenummer DESC);

-- Aggregatroten. Skrives kun én gang og oppdateres aldri; en ny prøving eller en ny manuell vurdering gir
-- en ny rad, slik at historikken består.
--
-- `id` er ikke en autogenerert kolonne, i motsetning til `løpenummer`: en kravvurdering migrert fra Spleis
-- skal beholde sin opprinnelige `opptjeningsvurderingId`.
--
-- `utfall` er kun satt når `kravkilde` er `OVERFOERT_FRA_INFOTRYGD` — da har vi ikke noen sti å utlede
-- utfallet fra. For alle andre kravkilder står utfallet i siste rad i `vilkarsvurdering`.
CREATE TABLE kravvurdering
(
    løpenummer          BIGSERIAL PRIMARY KEY,
    id                  UUID        NOT NULL UNIQUE,
    krav                TEXT        NOT NULL,
    fødselsnummer       VARCHAR(11) NOT NULL,
    skjæringstidspunkt  DATE        NOT NULL,
    kravkilde           TEXT        NOT NULL,
    utfall              TEXT,
    opprettet           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Oppslag av gjeldende kravvurdering for en nøkkel.
CREATE INDEX idx_kravvurdering_noekkel
    ON kravvurdering (krav, fødselsnummer, skjæringstidspunkt, løpenummer DESC);

-- Stien av vilkår som ble prøvd for å avgjøre kravet, i den rekkefølgen de ble prøvd. Innsettingsrekkefølgen
-- er alltid stiens rekkefølge — hele stien til en kravvurdering skrives i én operasjon — så løpenummeret
-- alene er nok til å lese den tilbake i riktig rekkefølge.
--
-- `kilde` bærer selv om vilkåret ble vurdert automatisk, manuelt eller er overført fra Spleis, og
-- prøvingen (når den finnes) ligger inni denne json-en og ikke i en egen kolonne — en prøving finnes kun
-- når kilden er automatisk, og en nullbar kolonne ville tillatt at det påstås en prøving som ikke er der.
CREATE TABLE vilkarsvurdering
(
    løpenummer          BIGSERIAL PRIMARY KEY,
    id                  UUID        NOT NULL UNIQUE,
    kravvurdering_id    UUID        NOT NULL REFERENCES kravvurdering (id),
    vilkårskode         TEXT        NOT NULL,
    utfall              TEXT        NOT NULL,
    vurdert_tidspunkt   TIMESTAMPTZ,
    kilde               JSONB       NOT NULL,
    opprettet           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vilkarsvurdering_kravvurdering_id
    ON vilkarsvurdering (kravvurdering_id, løpenummer);
