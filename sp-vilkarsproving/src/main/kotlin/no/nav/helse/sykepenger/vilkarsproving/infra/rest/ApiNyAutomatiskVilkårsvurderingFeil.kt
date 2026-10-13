package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.ApiErrorCode

internal enum class ApiNyAutomatiskVilkårsvurderingFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    OpptjeningsvurderingIkkeFunnet(404, "Fant ingen opptjeningsvurdering å vurdere på nytt"),
    OpptjeningsprøvingPågår(409, "Opptjeningsprøving pågår allerede"),
}
