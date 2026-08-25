package no.nav.helse.sykepenger.vilkarsproving.domain

/**
 * Faktaene et krav vurderes på. Ren data — reglene ligger i [Kravregel].
 *
 * Grunnlaget følger vurderingen, slik at vi i ettertid kan svare på hva vurderingen faktisk gjelder.
 */
internal sealed interface Vilkårsgrunnlag {
    val krav: Krav

    /** Behovet dette grunnlaget besvarer, eller null dersom det ikke må innhentes. */
    val besvarer: Grunnlagsbehov?
}
