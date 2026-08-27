package no.nav.helse.sykepenger.vilkarsproving.domain

import java.util.UUID

@JvmInline
internal value class OpptjeningsprøvingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = OpptjeningsprøvingId(UUID.randomUUID())
    }
}

@JvmInline
internal value class VilkårsvurderingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = VilkårsvurderingId(UUID.randomUUID())
    }
}

@JvmInline
internal value class OpptjeningsvurderingId(
    val value: UUID,
) {
    override fun toString() = value.toString()

    companion object {
        fun ny() = OpptjeningsvurderingId(UUID.randomUUID())
    }
}
