package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Kodeverkkode
import no.nav.helse.sykepenger.vilkarsproving.domain.Opphav
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsregel
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import java.time.LocalDate

internal object Vurderingsrespons {
    fun fra(vurdering: Vilkårsvurdering): ApiVilkårsvurderingerForPersonResponse =
        ApiVilkårsvurderingerForPersonResponse(
            skjæringstidspunkt = vurdering.skjæringstidspunkt,
            krav = listOf(vurdering.tilKravvurdering()),
        )
}

private fun Vilkårsvurdering.tilKravvurdering(): ApiKravvurdering =
    // Infotrygd-vurderinger bærer bare et utfall. Å bygge en sti for dem ville påstått at vi kjenner
    // vilkårene som ble prøvd, og det gjør vi ikke.
    when (val opphav = opphav) {
        is Opphav.Infotrygd ->
            ApiKravvurdering.OverførtFraInfotrygd(
                id = id.value,
                kravkode = vilkår.tilApi(),
                utfall = utfall.tilApi(),
            )

        is Opphav.Automatisk -> vurdertHosOss(opphav.tilApi(skjæringstidspunkt))
        is Opphav.Saksbehandler -> vurdertHosOss(opphav.tilApi())
    }

private fun Vilkårsvurdering.vurdertHosOss(kilde: ApiVurderingskilde): ApiKravvurdering.Vurdert {
    val sti =
        listOf(
            ApiVilkårsvurdering(
                id = id.value,
                vilkårskode = kodeverkkode.tilVilkårskode(),
                utfall = utfall.tilApi(),
                vurdertTidspunkt = vurdertTidspunkt,
                kilde = kilde,
            ),
        )
    return ApiKravvurdering.Vurdert(
        id = id.value,
        kravkode = vilkår.tilApi(),
        utfall = utfall.tilApi(),
        // Prøvingen stopper når kravet er avgjort, så det sist prøvde vilkåret er det avgjørende —
        // også når det er et unntak som slo ut et alternativ som ellers var oppfylt.
        avgjørendeVilkårskode = sti.last().vilkårskode,
        vurderinger = sti,
    )
}

private fun Vilkår.tilApi(): ApiKravkode =
    when (this) {
        Vilkår.Opptjening -> ApiKravkode.OPPTJENING
    }

private fun Utfall.tilApi(): ApiUtfall =
    when (this) {
        Utfall.Oppfylt -> ApiUtfall.OPPFYLT
        Utfall.IkkeOppfylt -> ApiUtfall.IKKE_OPPFYLT
    }

private fun Kodeverkkode.tilVilkårskode(): ApiVilkårskode =
    when (this) {
        Kodeverkkode.OPPTJENING_MINST_4_UKER -> ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
        Kodeverkkode.OPPTJENING_ANNEN_YTELSE -> ApiVilkårskode.OPPTJENING_LIKESTILT_YTELSE
        Kodeverkkode.OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER -> ApiVilkårskode.OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER
        Kodeverkkode.IKKE_OPPTJENING_AAP_FOER_FORELDREPENGER -> ApiVilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP

        // De generelle kodene sier bare at kravet er vurdert, ikke hvilket alternativ som traff.
        // TODO: Opptjeningsregel bruker i dag den generelle koden også når den faktisk har prøvd
        //  og forkastet fireukersvilkåret. Da mister vi presisjonen som stien er ment å gi. Rettes
        //  når domenet får én vurdering per alternativt vilkår.
        Kodeverkkode.OPPTJENING_ARBEID_ELLER_YTELSE,
        Kodeverkkode.IKKE_OPPTJENING_ARBEID_ELLER_YTELSE,
        -> ApiVilkårskode.OPPTJENING_ARBEID_ELLER_YTELSE
    }

private fun Opphav.Automatisk.tilApi(skjæringstidspunkt: LocalDate) =
    ApiVurderingskilde.Automatisk(
        versjonAvKildekode = versjonAvKildekode,
        grunnlag = grunnlag.tilApi(skjæringstidspunkt),
    )

private fun Opphav.Saksbehandler.tilApi() = ApiVurderingskilde.Saksbehandler(ident = ident, fritekstbegrunnelse = fritekstbegrunnelse)

/**
 * Opptjeningsperioden og antall opptjeningsdager er ikke lagret, og utledes ved å kjøre regelen på
 * nytt på det lagrede grunnlaget.
 *
 * TODO: Dette er en sporbarhetsbrist. Endres regelen, viser api-et nye tall under den gamle
 *  `versjonAvKildekode`-etiketten. De utledede verdiene bør lagres sammen med grunnlaget, slik at en
 *  vurdering aldri regnes ut på nytt ved lesing.
 */
private fun Vilkårsgrunnlag.tilApi(skjæringstidspunkt: LocalDate): ApiVurderingsgrunnlag =
    when (this) {
        is Opptjeningsgrunnlag.Arbeidstaker -> {
            val resultat = Opptjeningsregel.vurder(skjæringstidspunkt, this)
            ApiVurderingsgrunnlag.Arbeidsforhold(
                arbeidsforhold = arbeidsforhold.map { it.tilApi() },
                opptjeningsperiode = resultat.opptjeningsperiode?.tilApi(),
                // Uten en opptjeningsperiode fram til skjæringstidspunktet er det null dager opptjening.
                opptjeningsdager = resultat.opptjeningsdager ?: 0,
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
