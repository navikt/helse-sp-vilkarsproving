package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.speil.backend.app.logging.MdcKey

/**
 * MDC-nøkler for fødselsnummer og skjæringstidspunkt slik at [OpptjeningService] slipper å gjenta
 * disse i hver enkelt loggInfo-melding. Trygt fordi `logback.xml` kun slipper et fåtall
 * whitelistede MDC-felter gjennom til vanlig applikasjonslogg (`STDOUT_JSON`) — disse to nøklene
 * havner dermed kun i den beskyttede `team-logs`-loggeren, i tråd med regelen "fnr aldri i vanlig logg".
 */
internal enum class OpptjeningMdcKeys(
    override val value: String,
) : MdcKey {
    FØDSELSNUMMER("fødselsnummer"),
    SKJÆRINGSTIDSPUNKT("skjæringstidspunkt"),
    OPPTJENINGSVURDERING_ID("opptjeningsvurderingId"),
}
