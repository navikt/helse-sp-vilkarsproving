package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/vilkarsvurderinger/overstyring")
internal class ApiOverstyrVilkårsvurderingResource(
    val personId: String,
)
