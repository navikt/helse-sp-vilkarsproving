package no.nav.helse.sykepenger.vilkarsproving.domain

internal sealed interface Opptjeningsgrunnlag {
    val regel get() = Opptjeningsregel

    /** Behovet dette grunnlaget besvarer, eller null dersom det ikke må innhentes. */
    val besvarer: Grunnlagsbehov?

    data class Arbeidstaker(
        val arbeidsforhold: List<Arbeidsforhold>,
    ) : Opptjeningsgrunnlag {
        override val besvarer = Grunnlagsbehov.Arbeidsforhold
    }

    data object SelvstendigNæringsdrivende : Opptjeningsgrunnlag {
        override val besvarer = null
    }
}

internal enum class Grunnlagsbehov {
    Arbeidsforhold,
}
