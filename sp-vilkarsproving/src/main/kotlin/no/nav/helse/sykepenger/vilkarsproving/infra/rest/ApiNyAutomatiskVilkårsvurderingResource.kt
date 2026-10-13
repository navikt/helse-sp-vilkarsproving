package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/vilkarsvurderinger/automatisk")
internal class ApiNyAutomatiskVilkårsvurderingResource(
    val personId: String,
)
