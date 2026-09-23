package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.UtledetFakta
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering

internal fun SpleisOpptjeningsvurdering.tilOpptjeningsvurdering(fødselsnummer: String): Opptjeningsvurdering =
    when (this) {
        is SpleisOpptjeningsvurdering.SpleisArbeidstaker -> {
            val grunnlag = Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold.flatMap { it.tilDomene() })
            val utledetFakta = UtledetFakta.Opptjeningstid(opptjeningsperiode, antallDager)
            Opptjeningsvurdering.overførtFraSpleis(
                id = opptjeningsvurderingId,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsvurderinger =
                    listOf(
                        Vilkårsvurdering.overførtFraSpleis(
                            vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                            utfall = if (oppfylt) Utfall.Oppfylt else Utfall.IkkeOppfylt,
                            grunnlag = grunnlag,
                            utledetFakta = utledetFakta,
                        ),
                    ),
            )
        }

        is SpleisOpptjeningsvurdering.SpleisSelvstendig ->
            Opptjeningsvurdering.overførtFraSpleis(
                id = opptjeningsvurderingId,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsvurderinger =
                    listOf(
                        Vilkårsvurdering.overførtFraSpleis(
                            vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                            utfall = Utfall.Oppfylt,
                            grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
                            utledetFakta = UtledetFakta.Ingen,
                        ),
                    ),
            )

        is SpleisOpptjeningsvurdering.InfotrygdArbeidstaker ->
            Opptjeningsvurdering.fraInfotrygd(
                id = opptjeningsvurderingId,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                erOk = true,
            )
    }

private fun SpleisOpptjeningsvurdering.SpleisArbeidstaker.Arbeidsforhold.tilDomene(): List<Arbeidsforhold> =
    ansettelsesperioder.map { periode ->
        Arbeidsforhold(
            orgnummer = organisasjonsnummer,
            ansattFom = periode.fom,
            ansattTom = periode.tom,
            type = Arbeidsforhold.Arbeidsforholdtype.UKJENT,
        )
    }
