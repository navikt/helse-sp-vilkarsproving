package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/vilkarsvurderinger/manuell/arbeid-minst-fire-uker")
internal class ApiManuellArbeidMinstFireUkerVurderingResource(
    val personId: String,
)
