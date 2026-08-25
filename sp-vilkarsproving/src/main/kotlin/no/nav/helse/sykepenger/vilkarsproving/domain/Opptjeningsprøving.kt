package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.LocalDate

/**
 * Starter en prøving av opptjeningskravet.
 *
 * Arbeidstakere må vi hente arbeidsforhold for, mens selvstendig næringsdrivende kan vurderes
 * med en gang. Det er den eneste kravspesifikke delen av oppstarten — resten er [Kravprøving].
 */
internal object Opptjeningsprøving {
    fun start(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        arbeidssituasjon: Arbeidssituasjon,
    ) = Kravprøving.start(
        krav = Krav.Opptjening,
        fødselsnummer = fødselsnummer,
        skjæringstidspunkt = skjæringstidspunkt,
        behov = Grunnlagsbehov.Arbeidsforhold,
        umiddelbartGrunnlag =
            when (arbeidssituasjon) {
                Arbeidssituasjon.Arbeidstaker -> null
                Arbeidssituasjon.SelvstendigNæringsdrivende -> Opptjeningsgrunnlag.SelvstendigNæringsdrivende
            },
    )
}
