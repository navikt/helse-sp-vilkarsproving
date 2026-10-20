@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate

@Serializable
internal data class ApiManuellArbeidMinstFireUkerVurderingRequest(
    val skjæringstidspunkt: LocalDate,
    val utfall: ApiUtfall,
    val fritekstbegrunnelse: String,
    val journalpostId: List<String>,
)
