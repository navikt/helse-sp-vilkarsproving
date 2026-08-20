package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.speil.backend.app.rest.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.application.VilkårsprøvingService
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.sikkerLogg
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId

/**
 * Svarer på spørsmål om utfallet av en ferdig vurdering.
 *
 * Fordi en vurdering ser likt ut uansett vilkår, er denne riveren felles: den slår opp resultatet
 * direkte, uten å gå veien om prøvingen. Det er hele poenget med å skille resultat fra prosess.
 */
internal open class VilkårsvurderingResultatRiver(
    rapidsConnection: RapidsConnection,
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val vilkår: Vilkår,
    private val behovnavn: String,
    private val idFelt: String,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", "behov")
                    it.requireAllOrAny("@behov", listOf(behovnavn))
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
        val vurderingId = VurderingId(packet[idFelt].asUUID())
        sikkerLogg.info("Mottatt behov for $behovnavn for $idFelt $vurderingId")
        val vurdering =
            transaksjonProvider.transaksjon { kontekst ->
                VilkårsprøvingService(kontekst).finnVurdering(vilkår, vurderingId)
            }

        val ok =
            when (vurdering.utfall) {
                Utfall.Oppfylt -> true
                Utfall.IkkeOppfylt -> false
            }

        packet["@løsning"] = mapOf(behovnavn to mapOf("ok" to ok))
        sikkerLogg.info("Publiserer løsning for $behovnavn for $idFelt $vurderingId. Løsning:\n\t${packet.toJson()}")
        context.publish(packet.toJson())
    }
}

internal class OpptjeningsvurderingResultatRiver(
    rapidsConnection: RapidsConnection,
    transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
) : VilkårsvurderingResultatRiver(
        rapidsConnection = rapidsConnection,
        transaksjonProvider = transaksjonProvider,
        vilkår = Vilkår.Opptjening,
        behovnavn = "OpptjeningsvurderingResultat",
        idFelt = "OpptjeningsvurderingResultat.opptjeningsvurderingId",
    )
