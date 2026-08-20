@file:kotlinx.serialization.UseContextualSerialization(java.util.UUID::class, java.time.Instant::class, java.time.LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.rest

import kotlinx.serialization.Serializable
import no.nav.helse.sykepenger.vilkarsproving.domain.Kilde
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Responsen for GET .../vilkarsvurderinger: alt som er vurdert for personen, eldste først (eller kun én, se [VilkårsvurderingerForPersonResource]). */
@Serializable
internal data class VilkårsvurderingerForPersonResponse(
    val vurderinger: List<VilkårsvurderingResponse>,
)

@Serializable
internal data class VilkårsvurderingResponse(
    val id: UUID,
    val vilkår: Vilkår,
    val skjæringstidspunkt: LocalDate,
    val utfall: Utfall,
    val kodeverkkode: String,
    val kilde: KildeResponse,
    val vurdertTidspunkt: Instant,
)

@Serializable
internal sealed interface KildeResponse {
    @Serializable
    data class Automatisk(
        val regelversjon: String,
    ) : KildeResponse

    @Serializable
    data class Manuell(
        val saksbehandlerIdent: String,
        val fritekstbegrunnelse: String,
    ) : KildeResponse
}

internal fun Vilkårsvurdering.tilResponse() =
    VilkårsvurderingResponse(
        id = id.value,
        vilkår = vilkår,
        skjæringstidspunkt = skjæringstidspunkt,
        utfall = utfall,
        kodeverkkode = kodeverkkode.name,
        kilde =
            when (val kilde = kilde) {
                is Kilde.Automatisk -> KildeResponse.Automatisk(kilde.regelversjon)
                is Kilde.Manuell -> KildeResponse.Manuell(kilde.saksbehandlerIdent, kilde.fritekstbegrunnelse)
            },
        vurdertTidspunkt = vurdertTidspunkt,
    )
