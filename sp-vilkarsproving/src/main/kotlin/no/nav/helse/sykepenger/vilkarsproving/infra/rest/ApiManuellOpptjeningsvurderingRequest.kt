@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate

@Serializable
internal data class ApiManuellOpptjeningsvurderingRequest(
    val skjæringstidspunkt: LocalDate,
    val minst4Uker: Boolean,
    val opptjeningsaktiviteter: List<ApiOpptjeningsaktivitet>,
    val foreldrepengerOpptjentPåAap: Boolean?,
    val yrkesaktivMinst4UkerFørUttak: Boolean?,
    val fritekstbegrunnelse: String,
    val journalpostId: List<String>,
)
