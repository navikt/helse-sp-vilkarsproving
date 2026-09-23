package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.speil.backend.app.person.Identitetsnummer
import java.time.LocalDate
import java.util.*

@JvmInline
internal value class OutboxKonvoluttId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = OutboxKonvoluttId(UUID.randomUUID())
    }
}

internal data class OutboxKonvolutt(
    val id: OutboxKonvoluttId,
    val melding: OutboxMelding,
    val identitetsnummer: Identitetsnummer,
) {
    companion object {
        fun ny(
            melding: OutboxMelding,
            identitetsnummer: Identitetsnummer,
        ) = OutboxKonvolutt(
            id = OutboxKonvoluttId.ny(),
            melding = melding,
            identitetsnummer = identitetsnummer,
        )
    }
}

internal sealed interface OutboxMelding {
    data class OpptjeningsvurderingEndret(
        val skjæringstidspunkt: LocalDate,
        val opptjeningsvurderingId: UUID,
        val manuellVurdering: Boolean,
    ) : OutboxMelding
}

internal interface Outbox {
    fun leggTil(konvolutt: OutboxKonvolutt)

    fun hentUpubliserte(maksAntall: Int = 50): List<OutboxKonvolutt>

    fun markerSomSendt(id: OutboxKonvoluttId)
}
