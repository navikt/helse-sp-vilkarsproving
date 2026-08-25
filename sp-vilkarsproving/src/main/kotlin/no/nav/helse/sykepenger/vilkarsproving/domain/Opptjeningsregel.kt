package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.forrigeDag
import no.nav.helse.hendelser.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.hendelser.til
import java.time.LocalDate

internal object Opptjeningsregel : Kravregel {
    override val krav = Krav.Opptjening
    override val versjon = "1"

    private const val ANTALL_OPPTJENINGSDAGER_SOM_KREVES = 28

    override fun vurder(
        skjæringstidspunkt: LocalDate,
        grunnlag: Vilkårsgrunnlag,
    ): Kravregelresultat =
        when (grunnlag) {
            is Opptjeningsgrunnlag.Arbeidstaker -> vurderArbeidstaker(skjæringstidspunkt, grunnlag.arbeidsforhold)
            Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> vurderSelvstendigNæringsdrivende()
        }

    private fun vurderArbeidstaker(
        skjæringstidspunkt: LocalDate,
        arbeidsforhold: List<Arbeidsforhold>,
    ): Kravregelresultat {
        val opptjeningsperiode =
            arbeidsforhold
                .map { it.ansettelseperiode }
                .grupperSammenhengendePerioderMedHensynTilHelg()
                .find { skjæringstidspunkt.forrigeDag in it }
        val opptjeningsdager =
            opptjeningsperiode
                ?.subset(opptjeningsperiode.start til skjæringstidspunkt.forrigeDag)
                ?.count() ?: 0

        return Kravregelresultat(
            listOf(
                Vilkårsutfall(
                    vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                    utfall = if (opptjeningsdager >= ANTALL_OPPTJENINGSDAGER_SOM_KREVES) Utfall.Oppfylt else Utfall.IkkeOppfylt,
                    utledetFakta = UtledetFakta.Opptjeningstid(opptjeningsperiode, opptjeningsdager),
                ),
            ),
        )
    }

    private fun vurderSelvstendigNæringsdrivende() =
        Kravregelresultat(
            listOf(
                Vilkårsutfall(
                    vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                    utfall = Utfall.Oppfylt,
                    utledetFakta = UtledetFakta.Ingen,
                ),
            ),
        )
}
