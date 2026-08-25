package no.nav.helse.sykepenger.vilkarsproving.domain

/** Faktaene en opptjeningsvurdering er gjort på. Ren data — reglene ligger i [Opptjeningsregel]. */
internal sealed interface Opptjeningsgrunnlag : Vilkårsgrunnlag {
    override val krav get() = Krav.Opptjening

    data class Arbeidstaker(
        val arbeidsforhold: List<Arbeidsforhold>,
    ) : Opptjeningsgrunnlag {
        override val besvarer = Grunnlagsbehov.Arbeidsforhold
    }

    data object SelvstendigNæringsdrivende : Opptjeningsgrunnlag {
        override val besvarer = null
    }
}
