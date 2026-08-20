package no.nav.helse.sykepenger.vilkarsproving.rest

import no.nav.helse.speil.backend.app.rest.ApiErrorCode

internal enum class VilkårsvurderingerForPersonFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    ManglerRequestParameter(400, "Mangler request parameter"),
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    VurderingIkkeFunnet(404, "Vurdering ikke funnet"),
}
