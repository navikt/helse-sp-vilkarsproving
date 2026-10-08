@file:UseContextualSerialization(Instant::class, LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.Instant
import java.time.LocalDate

@Serializable
internal data class ApiOpptjeningshistorikkResponse(
    val skjæringstidspunkt: LocalDate,
    val historikk: List<ApiOpptjeningshistorikkInnslag>,
)

@Serializable
internal data class ApiOpptjeningshistorikkInnslag(
    val vurdertTidspunkt: Instant,
    val opptjeningsvurdering: ApiOpptjeningsvurdering,
)
