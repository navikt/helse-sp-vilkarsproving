package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.speil.backend.app.rest.ApiErrorCode

internal enum class ApiOverstyrVilkårsvurderingFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
}
