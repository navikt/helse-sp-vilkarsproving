package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import java.time.LocalDate

internal data class ApiManuellVilkårsvurderingRequest(
    val skjæringstidspunkt: LocalDate,
    val vilkårskode: ApiVilkårskode,
    val utfall: ApiUtfall,
    val fritekstbegrunnelse: String,
    val journalpostId: List<String>,
)
