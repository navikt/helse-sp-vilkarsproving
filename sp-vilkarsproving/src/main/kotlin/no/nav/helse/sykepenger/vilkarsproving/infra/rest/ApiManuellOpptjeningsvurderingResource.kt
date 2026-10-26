package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/vilkarsvurderinger/manuell/opptjening")
internal class ApiManuellOpptjeningsvurderingResource(
    val personId: String,
)
