package no.nav.helse.sykepenger.vilkarsproving.domain

import java.util.UUID

@JvmInline
internal value class PrøvingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = PrøvingId(UUID.randomUUID())
    }
}

@JvmInline
internal value class VurderingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = VurderingId(UUID.randomUUID())
    }
}
