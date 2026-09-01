package no.nav.helse.sykepenger.vilkarsproving.domain

internal sealed interface Vilkårsgrunnlag {
    val krav: Krav

    /** Behovet dette grunnlaget besvarer, eller null dersom det ikke må innhentes. */
    val besvarer: Grunnlagsbehov?
}
