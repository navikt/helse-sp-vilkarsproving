package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMeldingId
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.application.UtgåendeLøsning
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import java.time.LocalDate

internal object OpptjeningsvurderingOverstyrtMelding {
    private const val EVENT_NAME = "endret_opptjeningsvurdering"

    fun publiser(
        kontekst: Transaksjonskontekst,
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
        kontekst.outbox.leggTil(
            UtgåendeLøsning(
                id = OutboxMeldingId.ny(),
                meldingJson = melding.toJson(),
                fødselsnummer = fødselsnummer,
            ),
        )
    }
}
