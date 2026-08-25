package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Utledet
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import java.time.LocalDate

internal object Vurderingsrespons {
    fun fra(vurdering: Kravvurdering): ApiVilkårsvurderingerForPersonResponse =
        ApiVilkårsvurderingerForPersonResponse(
            skjæringstidspunkt = vurdering.skjæringstidspunkt,
            krav = listOf(vurdering.tilApi()),
        )
}

private fun Kravvurdering.tilApi(): ApiKravvurdering =
    when (this) {
        is Kravvurdering.OverførtFraInfotrygd ->
            ApiKravvurdering.OverførtFraInfotrygd(
                id = id.value,
                kravkode = krav.tilApi(),
                rettTilSykepenger = girRettTilSykepenger,
            )

        is Kravvurdering.VurdertISpeil ->
            ApiKravvurdering.VurdertISpeil(
                id = id.value,
                kravkode = krav.tilApi(),
                rettTilSykepenger = girRettTilSykepenger,
                avgjørendeVilkårskode = avgjørendeVilkårskode.tilApi(),
                vurderinger = vilkårsvurderinger.map { it.tilApi() },
            )
    }

private fun Vilkårsvurdering.tilApi() =
    ApiVilkårsvurdering(
        id = id.value,
        vilkårskode = vilkårskode.tilApi(),
        utfall = utfall.tilApi(),
        vurdertTidspunkt = vurdertTidspunkt,
        kilde = kilde.tilApi(),
    )

private fun Krav.tilApi(): ApiKravkode =
    when (this) {
        Krav.Opptjening -> ApiKravkode.OPPTJENING
    }

private fun Utfall.tilApi(): ApiUtfall =
    when (this) {
        Utfall.Oppfylt -> ApiUtfall.OPPFYLT
        Utfall.IkkeOppfylt -> ApiUtfall.IKKE_OPPFYLT
    }

private fun Vilkårskode.tilApi(): ApiVilkårskode =
    when (this) {
        Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER -> ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
        Vilkårskode.OPPTJENING_LIKESTILT_YTELSE -> ApiVilkårskode.OPPTJENING_LIKESTILT_YTELSE
        Vilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP ->
            ApiVilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP
        Vilkårskode.OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER -> ApiVilkårskode.OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER
    }

private fun Vurderingskilde.tilApi(): ApiVurderingskilde =
    when (this) {
        is Vurderingskilde.Automatisk ->
            ApiVurderingskilde.Automatisk(
                versjonAvKildekode = versjonAvKildekode,
                grunnlag = grunnlag.tilApi(utledet),
            )

        is Vurderingskilde.Saksbehandler ->
            ApiVurderingskilde.Saksbehandler(ident = ident, fritekstbegrunnelse = fritekstbegrunnelse)

        is Vurderingskilde.OverførtFraSpleis ->
            ApiVurderingskilde.OverførtFraSpleis(grunnlag = grunnlag.tilApi(utledet))
    }

private fun Vilkårsgrunnlag.tilApi(utledet: Utledet): ApiVurderingsgrunnlag =
    when (this) {
        is Opptjeningsgrunnlag.Arbeidstaker -> {
            check(utledet is Utledet.Opptjeningstid) {
                "Arbeidstaker-grunnlag skal ha utledet opptjeningstid, fikk $utledet"
            }
            ApiVurderingsgrunnlag.Arbeidsforhold(
                arbeidsforhold = arbeidsforhold.map { it.tilApi() },
                opptjeningsperiode = utledet.opptjeningsperiode?.tilApi(),
                opptjeningsdager = utledet.opptjeningsdager,
            )
        }

        Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> ApiVurderingsgrunnlag.SelvstendigNæringsdrivende()
    }

private fun no.nav.helse.hendelser.Periode.tilApi() = ApiPeriode(fom = start, tom = endInclusive)

private fun Arbeidsforhold.tilApi() =
    ApiArbeidsforhold(
        organisasjonsnummer = orgnummer,
        fom = ansettelseperiode.start,
        tom = ansettelseperiode.endInclusive.takeUnless { it == LocalDate.MAX },
        type =
            when (type) {
                Arbeidsforhold.Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING -> ApiArbeidsforholdtype.FORENKLET_OPPGJØRSORDNING
                Arbeidsforhold.Arbeidsforholdtype.FRILANSER -> ApiArbeidsforholdtype.FRILANSER
                Arbeidsforhold.Arbeidsforholdtype.MARITIMT -> ApiArbeidsforholdtype.MARITIMT
                Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT -> ApiArbeidsforholdtype.ORDINÆRT
            },
    )
