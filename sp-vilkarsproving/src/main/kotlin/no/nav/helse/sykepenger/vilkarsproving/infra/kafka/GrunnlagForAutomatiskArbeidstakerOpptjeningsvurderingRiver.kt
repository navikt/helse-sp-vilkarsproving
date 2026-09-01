package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asOptionalLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.speil.backend.app.logging.loggInfo
import no.nav.helse.speil.backend.app.logging.loggWarn
import no.nav.helse.speil.backend.app.logging.medMdc
import no.nav.helse.speil.backend.app.rest.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningMdcKeys
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

internal class GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
) : River.PacketListener {
    private val behovKey = "ArbeidsforholdV2"

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(behovKey))
                    it.requireValue("@final", true)
                    it.requireKey("fødselsnummer")
                    it.requireKey("opprinneligBehov")
                    it.requireKey("@løsning")
                }

                validate {
                    it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                    it.requireArray("@løsning.$behovKey") {
                        requireKey("orgnummer")
                        requireAny("type", listOf("FORENKLET_OPPGJØRSORDNING", "FRILANSER", "MARITIMT", "ORDINÆRT"))
                        require("ansattSiden", JsonNode::asLocalDate)
                        interestedIn("ansattTil", JsonNode::asLocalDate)
                    }
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val arbeidsforhold = packet.mapArbeidsforhold()

        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val fødselsnummer = packet["fødselsnummer"].asString()

        medMdc(
            OpptjeningMdcKeys.FØDSELSNUMMER to fødselsnummer,
            OpptjeningMdcKeys.SKJÆRINGSTIDSPUNKT to skjæringstidspunkt.toString(),
        ) {
            loggInfo(
                "Mottatt løsning på behov for $behovKey",
                "antallArbeidsforhold" to arbeidsforhold.size,
            )
            val resultat =
                transaksjonProvider.transaksjon { kontekst ->
                    OpptjeningService(kontekst).behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
                        fødselsnummer = fødselsnummer,
                        skjæringstidspunkt = skjæringstidspunkt,
                        arbeidsforhold = arbeidsforhold,
                    )
                }
            // Publisering skjer først etter at transaksjonen er commitet — vi forteller aldri
            // omverdenen om en vurdering vi ikke har lagret.
            when (resultat) {
                OpptjeningService.BehandleGrunnlagResultat.AlleredeVurdert -> {
                    loggWarn("Allerede vurdert. Ingen ny vurdering foretatt")
                    // No-op. finn ut av lognivå
                }
                is OpptjeningService.BehandleGrunnlagResultat.NyVurderingForetatt -> {
                    loggInfo(
                        "Ny vurdering foretatt",
                        "opptjeningsvurderingId" to resultat.opptjeningsvurderingId,
                    )
                    val opprinneligBehov = packet["opprinneligBehov"] as ObjectNode
                    val løsning = opprinneligBehov.putObject("@løsning")
                    løsning
                        .putObject("Opptjeningsvurdering")
                        .put("id", resultat.opptjeningsvurderingId.toString())
                    val løsningString = opprinneligBehov.toString()
                    loggInfo(
                        "Publiserer løsning på behov for opptjeningsvurdering",
                        "opptjeningsvurderingId" to resultat.opptjeningsvurderingId,
                        "løsning" to løsningString,
                    )
                    context.publish(løsningString)
                }

                OpptjeningService.BehandleGrunnlagResultat.IngenPrøvingFunnet -> {
                    loggWarn("Ingen prøving funnet")
                    // No op med warning logging om vi ikke logger i servicen
                }
            }
        }
    }

    private fun JsonMessage.mapArbeidsforhold() = mapArbeidsforhold(this["@løsning.$behovKey"])

    private fun mapArbeidsforhold(arbeidsforhold: JsonNode) =
        arbeidsforhold
            .filterNot { it["orgnummer"].asString().isBlank() }
            .filter {
                val til = it["ansattTil"].asOptionalLocalDate()
                til == null || it["ansattSiden"].asLocalDate() <= til
            }.map {
                Arbeidsforhold(
                    orgnummer = it["orgnummer"].asString(),
                    ansattFom = it["ansattSiden"].asLocalDate(),
                    ansattTom = it["ansattTil"].asOptionalLocalDate(),
                    type =
                        when (it["type"].asString()) {
                            "FORENKLET_OPPGJØRSORDNING" -> Arbeidsforhold.Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING
                            "FRILANSER" -> Arbeidsforhold.Arbeidsforholdtype.FRILANSER
                            "MARITIMT" -> Arbeidsforhold.Arbeidsforholdtype.MARITIMT
                            "ORDINÆRT" -> Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
                            else -> error("har ikke mappingregel for arbeidsforholdtype: ${it["type"].asString()}")
                        },
                )
            }
}
