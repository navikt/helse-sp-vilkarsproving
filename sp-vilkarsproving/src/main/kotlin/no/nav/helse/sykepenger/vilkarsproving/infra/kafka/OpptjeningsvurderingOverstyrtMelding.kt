package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import java.time.LocalDate

internal object OpptjeningsvurderingOverstyrtMelding {
    private const val EVENT_NAME = "endret_opptjeningsvurdering"

    fun publiser(
        context: MessageContext,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        opptjeningsvurderingId: OpptjeningsvurderingId,
    ) {
        val melding =
            JsonMessage.newMessage(
                eventName = EVENT_NAME,
                map =
                    mapOf(
                        "fødselsnummer" to fødselsnummer,
                        "skjæringstidspunkt" to skjæringstidspunkt,
                        "opptjeningsvurderingId" to opptjeningsvurderingId.value,
                        "manuellVurdering" to true,
                    ),
            )
        context.publish(fødselsnummer, melding.toJson())
    }
}
