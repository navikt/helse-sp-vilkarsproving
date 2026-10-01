package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode.Companion.grupperSammenhengendePerioderMedHensynTilHelg
import no.nav.helse.forrigeDag
import no.nav.helse.til
import java.time.LocalDate

internal object Opptjeningsregel {
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
        val dagenFør = skjæringstidspunkt.forrigeDag
        val opptjeningsperiode =
            arbeidsforhold
                .filter { it.type != Arbeidsforhold.Arbeidsforholdtype.FRILANSER }
                .map { it.ansettelseperiode }
                .filterNot { it.start > dagenFør }
                .map { it.subset(it.start til dagenFør) }
                .grupperSammenhengendePerioderMedHensynTilHelg()
                .find { it.erRettFør(skjæringstidspunkt) }
                // Et arbeidsforhold som slutter fredag/lørdag regnes som løpende over helgen fram til skjæringstidspunktet
                ?.let { it.start til dagenFør }
        val opptjeningsdager = opptjeningsperiode?.count() ?: 0

        return OpptjeningsregelResultat(
            listOf(
                Vilkårsutfall(
                    vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                    utfall = if (opptjeningsdager >= ANTALL_OPPTJENINGSDAGER_SOM_KREVES) Utfall.Oppfylt else Utfall.IkkeOppfylt,
                    utledetFakta = UtledetFakta.Opptjeningstid(opptjeningsperiode, opptjeningsdager),
                    lovreferanse = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER.lovreferanse,
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
                    lovreferanse = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER.lovreferanse,
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
}

internal data class Vilkårsutfall(
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val utledetFakta: UtledetFakta,
    val lovreferanse: Lovreferanse,
)
