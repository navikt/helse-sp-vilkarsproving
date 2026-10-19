package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.ApiErrorCode

internal enum class ApiManuellLikestiltYtelseVurderingFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    YtelserMangler(400, "Minst én likestilt ytelse må velges når arbeid og ytelser sammenlagt utgjør minst 4 uker"),
    YtelserUtenSammenlagtOpptjening(400, "Ytelser kan bare oppgis når arbeid og ytelser sammenlagt utgjør minst 4 uker"),
    ForeldrepengerOpptjentPåAapMangler(400, "Må svare på om foreldrepengene er opptjent på AAP"),
    ForeldrepengerOpptjentPåAapUtenForeldrepenger(400, "Kan bare svare på om foreldrepengene er opptjent på AAP når foreldrepenger er valgt"),
    YrkesaktivFørUttakMangler(400, "Må svare på om søkeren var yrkesaktiv i minst 4 uker før uttaket av foreldrepenger"),
    YrkesaktivFørUttakUtenForeldrepengerOpptjentPåAap(
        400,
        "Kan bare svare på yrkesaktivitet før uttaket når foreldrepengene er opptjent på AAP",
    ),
}
