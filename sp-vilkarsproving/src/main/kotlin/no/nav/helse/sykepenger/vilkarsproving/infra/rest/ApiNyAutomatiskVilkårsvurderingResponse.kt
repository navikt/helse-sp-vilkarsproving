@file:UseContextualSerialization(UUID::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.util.UUID

@Serializable
internal data class ApiNyAutomatiskVilkårsvurderingResponse(
    val status: ApiNyAutomatiskVilkårsvurderingStatus,
    val opptjeningsvurderingId: UUID? = null,
)

@Serializable
internal enum class ApiNyAutomatiskVilkårsvurderingStatus {
    VURDERT,
    VENTER_PÅ_GRUNNLAG,
}
