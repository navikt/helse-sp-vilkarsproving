@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate

@Resource("/api/personer/{personId}/opptjeningsvurderinger/historikk")
internal class ApiOpptjeningshistorikkResource(
    val personId: String,
    val skjæringstidspunkt: LocalDate,
)
