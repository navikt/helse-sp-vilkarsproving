package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.ApiErrorCode

internal enum class ApiOpptjeningshistorikkFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    SpleisUtilgjengelig(503, "Spleis er utilgjengelig"),
}
