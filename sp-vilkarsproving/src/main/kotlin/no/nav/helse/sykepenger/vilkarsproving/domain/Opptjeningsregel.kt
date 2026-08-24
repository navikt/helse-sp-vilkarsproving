package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.hendelser.til
import java.time.LocalDate

internal object Opptjeningsregel : Vilkårsregel {
    override val vilkår = Vilkår.Opptjening
    override val versjon = "1"

    private const val ANTALL_OPPTJENINGSDAGER_SOM_KREVES = 28

    override fun vurder(
        skjæringstidspunkt: LocalDate,
        grunnlag: Vilkårsgrunnlag,
    ): Resultat =
        when (grunnlag) {
            is Opptjeningsgrunnlag.Arbeidstaker -> vurderArbeidstaker(skjæringstidspunkt, grunnlag.arbeidsforhold)
            Opptjeningsgrunnlag.SelvstendigNæringsdrivende ->
                Resultat(
                    opptjeningsperiode = null,
                    opptjeningsdager = null,
                    kodeverkkode = Kodeverkkode.OPPTJENING_MINST_4_UKER,
                )
        }

    private fun vurderArbeidstaker(
        skjæringstidspunkt: LocalDate,
        arbeidsforhold: List<Arbeidsforhold>,
    ): Resultat {
        val opptjeningsperiode =
            arbeidsforhold
                .map { it.ansettelseperiode }
                .grupperSammenhengendePerioderMedHensynTilHelg()
                .find { skjæringstidspunkt.forrigeDag in it }
        val opptjeningsdager =
            opptjeningsperiode
                ?.subset(opptjeningsperiode.start til skjæringstidspunkt.forrigeDag)
                ?.count()

        return Resultat(
            opptjeningsperiode = opptjeningsperiode,
            opptjeningsdager = opptjeningsdager,
            kodeverkkode =
                if ((opptjeningsdager ?: 0) >= ANTALL_OPPTJENINGSDAGER_SOM_KREVES) {
                    Kodeverkkode.OPPTJENING_MINST_4_UKER
                } else {
                    Kodeverkkode.IKKE_OPPTJENING_ARBEID_ELLER_YTELSE
                },
        )
    }

    data class Resultat(
        val opptjeningsperiode: Periode?,
        val opptjeningsdager: Int?,
        override val kodeverkkode: Kodeverkkode,
    ) : Vilkårsregelresultat
}
