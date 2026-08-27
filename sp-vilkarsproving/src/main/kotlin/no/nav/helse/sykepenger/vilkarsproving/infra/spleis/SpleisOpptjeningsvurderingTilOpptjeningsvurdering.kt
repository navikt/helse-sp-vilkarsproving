package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.UtledetFakta
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("OpptjeningsvurderingTilOpptjeningsvurdering")

/**
 * Oversetter en [SpleisOpptjeningsvurdering] hentet fra spleis-api til domeneobjektet [Opptjeningsvurdering],
 * slik at den kan behandles likt med vurderinger hentet fra vår egen database — se
 * [no.nav.helse.sykepenger.vilkarsproving.infra.db.Lagringsjson] for tilsvarende oversettelse
 * for lagrede vurderinger.
 */
internal fun SpleisOpptjeningsvurdering.tilOpptjeningsvurdering(fødselsnummer: String): Opptjeningsvurdering =
    when (this) {
        is SpleisOpptjeningsvurdering.SpleisArbeidstaker -> {
            val grunnlag = Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold.flatMap { it.tilDomene() })
            val utledetFakta = UtledetFakta.Opptjeningstid(opptjeningsperiode, antallDager)
            Opptjeningsvurdering.overførtFraSpleis(
                id = opptjeningsvurderingId,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                sti =
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
                sti =
                    listOf(
                        Vilkårsvurdering.overførtFraSpleis(
                            vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                            utfall = Utfall.Oppfylt,
                            grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
                            utledetFakta = UtledetFakta.Ingen,
                        ),
                    ),
            )

        // Infotrygd-krav har verken sti eller avgjørende vilkår i vår modell. Spleis overfører kun
        // opptjeningsvurderinger som Infotrygd har innvilget, så vi kan trygt anta rett til
        // sykepenger — jf. samme antakelse i OpptjeningsvurderingResultatRiver.
        is SpleisOpptjeningsvurdering.InfotrygdArbeidstaker ->
            Opptjeningsvurdering.fraInfotrygd(
                id = opptjeningsvurderingId,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                girRettTilSykepenger = true,
            )
    }

private fun SpleisOpptjeningsvurdering.SpleisArbeidstaker.Arbeidsforhold.tilDomene(): List<Arbeidsforhold> {
    // Spleis oppgir ikke arbeidsforholdtype (ordinært, frilanser, maritimt o.l.), kun
    // ansettelsesperioder per orgnummer. Vi setter UKJENT inntil spleis-api eventuelt utvides
    // til å oppgi reell type.
    log.warn(
        "Mangler arbeidsforholdtype fra spleis-api for orgnummer $organisasjonsnummer — setter UKJENT",
    )
    return ansettelsesperioder.map { periode ->
        Arbeidsforhold(
            orgnummer = organisasjonsnummer,
            ansattFom = periode.fom,
            ansattTom = periode.tom,
            type = Arbeidsforhold.Arbeidsforholdtype.UKJENT,
        )
    }
}
