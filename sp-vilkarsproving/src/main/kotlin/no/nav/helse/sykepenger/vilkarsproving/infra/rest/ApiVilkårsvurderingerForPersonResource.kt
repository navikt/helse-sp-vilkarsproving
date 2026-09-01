@file:UseContextualSerialization(UUID::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource
import kotlinx.serialization.UseContextualSerialization
import java.util.UUID

@Resource("/api/personer/{personId}/vilkarsvurderinger")
internal class ApiVilkårsvurderingerForPersonResource(
    val personId: String,
    val opptjeningsvurderingId: UUID,
)
