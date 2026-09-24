package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.Periode
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.UtledetFakta
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import java.time.LocalDate

internal object Vurderingsrespons {
    fun fra(vurdering: Opptjeningsvurdering): ApiVilkårsvurderingerForPersonResponse =
        ApiVilkårsvurderingerForPersonResponse(
            skjæringstidspunkt = vurdering.skjæringstidspunkt,
            krav = listOf(vurdering.tilApi()),
        )
}

private fun Opptjeningsvurdering.tilApi(): ApiOpptjeningsvurdering =
    when (this) {
        is Opptjeningsvurdering.OverførtFraInfotrygd ->
            ApiOpptjeningsvurdering.OverførtFraInfotrygd(
                id = id.value,
                kravkode = ApiKravkode.OPPTJENING,
                opptjeningOk = erOk,
            )

        is Opptjeningsvurdering.VurdertISpeil ->
            ApiOpptjeningsvurdering.VurdertISpeil(
                id = id.value,
                kravkode = ApiKravkode.OPPTJENING,
                opptjeningOk = erOk,
                avgjørendeVilkårskode = avgjørendeVilkårskode?.tilApi(),
                vurderinger = vilkårsvurderinger.map { it.tilApi() },
            )
    }

private fun Vilkårsvurdering.tilApi() =
    ApiVilkårsvurdering(
        id = id.value,
        vilkårskode = vilkårskode.tilApi(),
        utfall = utfall.tilApi(),
        vurdertTidspunkt = vurdertTidspunkt,
        lovreferanse = lovreferanse.tilApi(),
        kilde = kilde.tilApi(),
    )

private fun no.nav.helse.sykepenger.vilkarsproving.domain.Lovreferanse.tilApi() =
    ApiLovreferanse(
        lov = lov,
        paragraf = paragraf,
        avsnitt = avsnitt,
        setning = setning,
        bokstav = bokstav,
        iKraftFra = iKraftFra,
    )

private fun Utfall.tilApi(): ApiUtfall =
    when (this) {
        Utfall.Oppfylt -> ApiUtfall.OPPFYLT
        Utfall.IkkeOppfylt -> ApiUtfall.IKKE_OPPFYLT
    }

private fun Vilkårskode.tilApi(): ApiVilkårskode =
    when (this) {
        Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER -> ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
    }

private fun Vurderingskilde.tilApi(): ApiVurderingskilde =
    when (this) {
        is Vurderingskilde.Automatisk ->
            ApiVurderingskilde.Automatisk(
                versjonAvKildekode = versjonAvKildekode,
                grunnlag = grunnlag.tilApi(utledetFakta),
            )

        is Vurderingskilde.Saksbehandler ->
            ApiVurderingskilde.Saksbehandler(ident = ident, fritekstbegrunnelse = fritekstbegrunnelse, journalpostId = journalpostId)

        is Vurderingskilde.OverførtFraSpleis ->
            ApiVurderingskilde.OverførtFraSpleis(grunnlag = grunnlag.tilApi(utledetFakta))
    }

private fun Opptjeningsgrunnlag.tilApi(utledetFakta: UtledetFakta): ApiVurderingsgrunnlag =
    when (this) {
        is Opptjeningsgrunnlag.Arbeidstaker -> {
            check(utledetFakta is UtledetFakta.Opptjeningstid) {
                "Arbeidstaker-grunnlag skal ha utledet opptjeningstid, fikk $utledetFakta"
            }
            ApiVurderingsgrunnlag.Arbeidsforhold(
                arbeidsforhold = arbeidsforhold.map { it.tilApi() },
                opptjeningsperiode = utledetFakta.opptjeningsperiode?.tilApi(),
                opptjeningsdager = utledetFakta.opptjeningsdager,
            )
        }

        Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> ApiVurderingsgrunnlag.SelvstendigNæringsdrivende()
    }

private fun Periode.tilApi() = ApiPeriode(fom = start, tom = endInclusive)

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
                Arbeidsforhold.Arbeidsforholdtype.UKJENT -> ApiArbeidsforholdtype.UKJENT
            },
    )
