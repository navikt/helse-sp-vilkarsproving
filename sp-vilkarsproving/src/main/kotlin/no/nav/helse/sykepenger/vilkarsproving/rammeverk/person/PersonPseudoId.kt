package no.nav.helse.sykepenger.vilkarsproving.rammeverk.person

import java.util.UUID

@JvmInline
value class PersonPseudoId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun fraString(raw: String): PersonPseudoId? = runCatching { PersonPseudoId(UUID.fromString(raw)) }.getOrNull()
    }
}
