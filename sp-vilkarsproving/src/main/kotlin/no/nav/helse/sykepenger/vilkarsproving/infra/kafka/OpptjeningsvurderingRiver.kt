package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService
import no.nav.helse.sykepenger.vilkarsproving.application.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.sikkerLogg
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper

internal class OpptjeningsvurderingRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider,
) : River.PacketListener {
    private val behovKey = "Opptjeningsvurdering"

    init {

        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireAllOrAny("@behov", listOf(behovKey))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("fødselsnummer")
                    it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                    it.requireKey("arbeidssituasjon") // TODO strengere validering
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        sikkerLogg.info("Mottatt behov for $behovKey for fødselsnummer ${packet["fødselsnummer"].asString()} med skjæringstidspunkt ${packet["skjæringstidspunkt"].asLocalDate()}")
        val fødselsnummer = packet["fødselsnummer"].asString()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val arbeidssituasjon = Arbeidssituasjon.valueOf(packet["arbeidssituasjon"].asString())

        val vurderOpptjeningResultat =
            transaksjonProvider.transaksjon { kontekst ->
                OpptjeningService(kontekst).vurderOpptjening(
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    arbeidssituasjon = arbeidssituasjon,
                )
            }

        // Publisering skjer først etter at transaksjonen er commitet — vi forteller aldri
        // omverdenen om en vurdering vi ikke har lagret.
        when (vurderOpptjeningResultat) {
            is VurderOpptjeningResultat.HarVurdering -> {
                packet["@løsning"] =
                    mapOf(
                        "Opptjeningsvurdering" to
                            mapOf(
                                "id" to vurderOpptjeningResultat.vurderingId.toString(),
                            ),
                    )
                sikkerLogg.info("Har vurdering for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. VurderingId: ${vurderOpptjeningResultat.vurderingId}. Løsning:\n\t${packet.toJson()}")
                context.publish(packet.toJson())
            }

            is VurderOpptjeningResultat.TrengerArbeidsforhold -> {
                val utgåendeBehov =
                    JsonMessage.newNeed(
                        behov = listOf("ArbeidsforholdV2"),
                        map =
                            mapOf(
                                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                                "fødselsnummer" to fødselsnummer,
                                "opprinneligBehov" to jacksonObjectMapper().readTree(packet.toJson()), // TODO vi må være sikker på json eller string her?
                            ),
                    )
                sikkerLogg.info("Trenger arbeidsforhold for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. Publiserer nytt behov:\n\t${utgåendeBehov.toJson()}")
                context.publish(utgåendeBehov.toJson())
            }
        }
    }
}
