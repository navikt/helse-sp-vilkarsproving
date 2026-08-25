package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.speil.backend.app.rest.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.sikkerLogg
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.Opptjeningsvurdering

/**
 * Svarer på spørsmål om utfallet av en ferdig kravvurdering.
 *
 * Fordi en kravvurdering ser likt ut uansett krav, er denne riveren felles: den slår opp resultatet
 * direkte, uten å gå veien om prøvingen. Det er hele poenget med å skille resultat fra prosess.
 */
internal open class OpptjeningsvurderingResultatRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val spleisClient: ISpleisClient,
) : River.PacketListener {
    private val krav = Krav.Opptjening
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
        val kravvurderingId = KravvurderingId(packet[idFelt].asUUID())
        val fødselsnummer = packet["fødselsnummer"].asString()
        sikkerLogg.info("Mottatt behov for $behovnavn for $idFelt $kravvurderingId")

        val kravvurdering =
            transaksjonProvider.transaksjon {
                it.kravvurderinger.finn(krav, kravvurderingId)
            }

        val utfall =
            kravvurdering?.utfall ?: spleisClient
                .hentOpptjeningsvurderinger(fødselsnummer = fødselsnummer)
                .find { it.opptjeningsvurderingId == kravvurderingId }
                ?.let { vurdering ->
                    when (vurdering) {
                        is Opptjeningsvurdering.SpleisArbeidstaker -> when (vurdering.oppfylt) {
                            true -> Utfall.Oppfylt
                            false -> Utfall.IkkeOppfylt
                        }
                        is Opptjeningsvurdering.SpleisSelvstendig,
                        is Opptjeningsvurdering.InfotrygdArbeidstaker -> Utfall.Oppfylt
                    }
                }?: error("Fant ikke vurdering med id $kravvurderingId")

        val ok =
            when (utfall) {
                Utfall.Oppfylt -> true
                Utfall.IkkeOppfylt -> false
            }

        packet["@løsning"] = mapOf(behovnavn to mapOf("ok" to ok))
        sikkerLogg.info("Publiserer løsning for $behovnavn for $idFelt $kravvurderingId. Løsning:\n\t${packet.toJson()}")
        context.publish(packet.toJson())
    }
}
