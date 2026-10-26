package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.ApiErrorCode

internal enum class ApiManuellOpptjeningsvurderingFeil(
    override val httpStatus: Int,
    override val tittel: String,
) : ApiErrorCode {
    PersonIkkeFunnet(404, "Person ikke funnet"),
    ManglerTilgang(403, "Mangler tilgang"),
    IkkeOppfyltStøttesIkke(400, "Det er ennå ikke støtte for å manuelt vurdere opptjening til ikke oppfylt"),
    LikestiltYtelseIkkeSkruddPå(400, "Manuell vurdering av likestilte ytelser er ikke skrudd på"),
    OpptjeningsaktiviteterMangler(400, "Minst én opptjeningsaktivitet må velges når søkeren har hatt minst 4 uker"),
    OpptjeningsaktiviteterUtenMinst4Uker(400, "Opptjeningsaktiviteter kan bare oppgis når søkeren har hatt minst 4 uker"),
    ForeldrepengerOpptjentPåAapMangler(400, "Må svare på om foreldrepengene er opptjent på AAP"),
    ForeldrepengerOpptjentPåAapUtenForeldrepenger(400, "Kan bare svare på om foreldrepengene er opptjent på AAP når foreldrepenger er valgt"),
    YrkesaktivFørUttakMangler(400, "Må svare på om søkeren var yrkesaktiv i minst 4 uker før uttaket av foreldrepenger"),
    YrkesaktivFørUttakUtenForeldrepengerOpptjentPåAap(
        400,
        "Kan bare svare på yrkesaktivitet før uttaket når foreldrepengene er opptjent på AAP",
    ),
}
