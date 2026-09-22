package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.speil.backend.app.logging.loggError
import no.nav.helse.speil.backend.app.logging.loggInfo
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.speil.backend.app.rest.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class OutboxPubliseringsjobb(
    private val rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val pollIntervall: Duration = 0.5.seconds,
) : RapidsConnection.StatusListener {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    override fun onStartup(rapidsConnection: RapidsConnection) {
        loggInfo("Starter outbox-publiseringsjobb")
        job =
            scope.launch {
                while (isActive) {
                    kjørEnRunde()
                    delay(pollIntervall)
                }
            }
    }

    override fun onShutdownSignal(rapidsConnection: RapidsConnection) {
        loggInfo("Stopper outbox-publiseringsjobb")
        runBlocking {
            job?.cancelAndJoin()
        }
    }

    internal fun kjørEnRunde() {
        try {
            transaksjonProvider.transaksjon { kontekst ->
                kontekst.outbox.hentUpubliserte().forEach { konvolutt ->
                    val json =
                        when (val melding = konvolutt.melding) {
                            is OutboxMelding.OpptjeningsvurderingOverstyrt -> melding.tilJsonMessage(konvolutt.identitetsnummer)
                        }
                    rapidsConnection.publish(konvolutt.identitetsnummer.value, json.toJson())
                    kontekst.outbox.markerSomSendt(konvolutt.id)
                }
            }
        } catch (e: Exception) {
            loggError("Feil under publisering av outbox-meldinger. Prøver igjen ved neste poll", e)
        }
    }

    private fun OutboxMelding.OpptjeningsvurderingOverstyrt.tilJsonMessage(identitetsnummer: Identitetsnummer) =
        JsonMessage.newMessage(
            eventName = "endret_opptjeningsvurdering",
            map =
                mapOf(
                    "fødselsnummer" to identitetsnummer,
                    "skjæringstidspunkt" to skjæringstidspunkt,
                    "opptjeningsvurderingId" to opptjeningsvurderingId,
                    "manuellVurdering" to manuellVurdering,
                ),
        )
}
