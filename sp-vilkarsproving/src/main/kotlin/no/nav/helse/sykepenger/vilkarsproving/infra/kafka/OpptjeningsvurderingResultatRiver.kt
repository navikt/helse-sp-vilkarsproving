package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.medMdc

internal open class OpptjeningsvurderingResultatRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val spleisClient: ISpleisClient,
) : River.PacketListener {
    private val behovnavn = "OpptjeningsvurderingResultat"
    private val idFelt = "OpptjeningsvurderingResultat.opptjeningsvurderingId"

    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(behovnavn))
                    it.forbid("@løsning")
                }
                validate {
                    it.requireKey("fødselsnummer")
                    it.requireKey(idFelt)
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val opptjeningsvurderingId = OpptjeningsvurderingId(packet[idFelt].asUUID())
        val fødselsnummer = packet["fødselsnummer"].asString()

        medMdc(
            MdcKey.IDENTITETSNUMMER to fødselsnummer,
        ) {
            loggInfo("Mottatt behov for $behovnavn", "opptjeningsvurderingId" to opptjeningsvurderingId.toString())

            try {
                val opptjeningsvurdering =
                    transaksjonProvider.transaksjon {
                        it.opptjeningsvurderinger.finn(opptjeningsvurderingId)
                    }

                val utfall =
                    opptjeningsvurdering?.erOk
                        ?: hentVurderingFraSpleis(fødselsnummer, opptjeningsvurderingId)
                        ?: error("Fant ikke vurdering med id $opptjeningsvurderingId")

                packet["@løsning"] = mapOf(behovnavn to mapOf("ok" to utfall))
                loggInfo(
                    "Publiserer løsning",
                    "løsning" to packet.toJson(),
                )
                context.publish(packet.toJson())
            } catch (ex: Exception) {
                loggError("Feil under håndtering av behov $behovnavn", ex, "opptjeningsvurderingId" to opptjeningsvurderingId.toString())
            }
        }
    }

    private fun hentVurderingFraSpleis(
        fødselsnummer: String,
        opptjeningsvurderingId: OpptjeningsvurderingId,
    ): Boolean? =
        spleisClient
            .hentOpptjeningsvurderinger(fødselsnummer = fødselsnummer)
            .find { it.opptjeningsvurderingId == opptjeningsvurderingId }
            ?.let { vurdering ->
                when (vurdering) {
                    is SpleisOpptjeningsvurdering.SpleisArbeidstaker -> vurdering.oppfylt

                    is SpleisOpptjeningsvurdering.SpleisSelvstendig,
                    is SpleisOpptjeningsvurdering.InfotrygdArbeidstaker,
                    -> true
                }
            }
}
