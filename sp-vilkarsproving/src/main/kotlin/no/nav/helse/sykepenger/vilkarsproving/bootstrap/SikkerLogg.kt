package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Beholdt som egen fil etter at [no.nav.helse.speil.backend.app.bootstrap.startApp] overtok
 * ansvaret for selve bootstrap-oppsettet (jf. cutover til `speil-backend-app`) — rivere og øvrig
 * appkode i dette modulet logger fortsatt persondetaljer via denne loggeren.
 */
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
