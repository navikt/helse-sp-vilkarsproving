package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.speil.backend.app.rest.ApiErrorCode

internal enum class ApiVilkårsvurderingerForPersonFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    VurderingIkkeFunnet(404, "Vurdering ikke funnet"),
    SpleisUtilgjengelig(503, "Spleis er utilgjengelig"),
}
