package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.sykepenger.vilkarsproving.application.PersonAvstemmingService

internal class PersonAvstemmingRiver(
    rapidsConnection: RapidsConnection,
    private val personAvstemmingService: PersonAvstemmingService,
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "person_avstemming") }
                validate {
                    it.requireKey("@id", "fødselsnummer")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        personAvstemmingService.lagreOpptjeningsvurderinger(fødselsnummer)
    }
}
