package no.nav.helse.sykepenger.vilkarsproving.application

import java.util.UUID

@JvmInline
internal value class OutboxMeldingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = OutboxMeldingId(UUID.randomUUID())
    }
}

internal data class UtgåendeLøsning(
    val id: OutboxMeldingId,
    val meldingJson: String,
    val fødselsnummer: String,
)

internal interface Outbox {
    fun leggTil(melding: UtgåendeLøsning)

    fun hentUpubliserte(maksAntall: Int = 50): List<UtgåendeLøsning>

    fun markerSomPublisert(id: OutboxMeldingId)
}
