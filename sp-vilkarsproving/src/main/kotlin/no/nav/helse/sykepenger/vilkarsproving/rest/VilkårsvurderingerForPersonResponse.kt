@file:kotlinx.serialization.UseContextualSerialization(UUID::class, Instant::class, LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.rest

import kotlinx.serialization.Serializable
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import java.time.Instant
import java.time.LocalDate
import java.util.*


@Serializable
internal data class ApiVilkårsvurderingerForPersonResponse(
    val opptjening: ApiOpptjening? = null
)

@Serializable
internal sealed interface ApiOpptjening {
    val id: UUID
    val utfall: ApiUtfall
    val kodeverkkode: String


    @Serializable
    data class Automatisk(
        val regelversjon: String,
        val opptjeningsperiode: ApiOpptjeningsperiode?,
        val antallDagerPåkrevd: Int,
        override val id: UUID,
        override val utfall: ApiUtfall,
        override val kodeverkkode: String,

        ) : ApiOpptjening

    @Serializable
    data class Manuell(
        val saksbehandlerIdent: String,
        val fritekstbegrunnelse: String,
        override val id: UUID,
        override val utfall: ApiUtfall,
        override val kodeverkkode: String,
    ) : ApiOpptjening
}

@Serializable
data class ApiOpptjeningsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDager: Int,
)


/** API-domenets utgave av [Vilkår] — API-et sitt kontraktsobjekt, uavhengig av det interne domenet. */
@Serializable
internal enum class ApiVilkår {
    Opptjening,
}

/** API-domenets utgave av [Utfall] — API-et sitt kontraktsobjekt, uavhengig av det interne domenet. */
@Serializable
internal enum class ApiUtfall {
    Oppfylt,
    IkkeOppfylt,
}



private fun Vilkår.tilApiVilkår() =
    when (this) {
        Vilkår.Opptjening -> ApiVilkår.Opptjening
    }

internal fun Utfall.tilApiUtfall() =
    when (this) {
        Utfall.Oppfylt -> ApiUtfall.Oppfylt
        Utfall.IkkeOppfylt -> ApiUtfall.IkkeOppfylt
    }
