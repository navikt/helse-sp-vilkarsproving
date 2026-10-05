package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
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
                            is OutboxMelding.OpptjeningsvurderingEndret -> melding.tilJsonMessage(konvolutt.identitetsnummer)
                        }
                    val (_, feiledeMeldinger) =
                        rapidsConnection.publish(
                            listOf(OutgoingMessage(body = json.toJson(), key = konvolutt.identitetsnummer.value)),
                        )
                    feiledeMeldinger.firstOrNull()?.apply { throw error }
                    kontekst.outbox.markerSomSendt(konvolutt.id)
                }
            }
        } catch (e: Exception) {
            loggError("Feil under publisering av outbox-meldinger. Prøver igjen ved neste poll", e)
        }
    }

    private fun OutboxMelding.OpptjeningsvurderingEndret.tilJsonMessage(identitetsnummer: Identitetsnummer) =
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
