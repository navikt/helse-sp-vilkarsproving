package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.forrigeDag
import no.nav.helse.til
import java.time.LocalDate

internal object Opptjeningsregel {
    val versjon = "1"

    private const val ANTALL_OPPTJENINGSDAGER_SOM_KREVES = 28

    fun vurder(
        skjæringstidspunkt: LocalDate,
        grunnlag: Opptjeningsgrunnlag,
    ): OpptjeningsregelResultat =
        when (grunnlag) {
            is Opptjeningsgrunnlag.Arbeidstaker -> vurderArbeidstaker(skjæringstidspunkt, grunnlag.arbeidsforhold)
            Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> vurderSelvstendigNæringsdrivende()
        }

    private fun vurderArbeidstaker(
        skjæringstidspunkt: LocalDate,
        arbeidsforhold: List<Arbeidsforhold>,
    ): OpptjeningsregelResultat {
        val opptjeningsperiode =
            arbeidsforhold
                .map { it.ansettelseperiode }
                .grupperSammenhengendePerioderMedHensynTilHelg()
                .find { skjæringstidspunkt.forrigeDag in it }
        val opptjeningsdager =
            opptjeningsperiode
                ?.subset(opptjeningsperiode.start til skjæringstidspunkt.forrigeDag)
                ?.count() ?: 0

        return OpptjeningsregelResultat(
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
        OpptjeningsregelResultat(
            listOf(
                Vilkårsutfall(
                    vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                    utfall = Utfall.Oppfylt,
                    utledetFakta = UtledetFakta.Ingen,
                ),
            ),
        )
}

internal data class OpptjeningsregelResultat(
    val vilkårsutfall: List<Vilkårsutfall>,
) {
    init {
        require(vilkårsutfall.isNotEmpty()) { "En regel må ha prøvd minst ett vilkår" }
    }

    val utfall: Utfall get() = vilkårsutfall.last().utfall
}

internal data class Vilkårsutfall(
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val utledetFakta: UtledetFakta,
)
