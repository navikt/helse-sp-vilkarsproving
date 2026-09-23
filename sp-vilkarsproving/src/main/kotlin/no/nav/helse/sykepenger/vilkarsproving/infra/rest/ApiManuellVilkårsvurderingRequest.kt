@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate

@Serializable
internal data class ApiManuellVilkårsvurderingRequest(
    val skjæringstidspunkt: LocalDate,
    val vilkårskode: ApiVilkårskode,
    val utfall: ApiUtfall,
    val fritekstbegrunnelse: String,
)
