package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.avgjørendeVilkårskode
import kotlin.test.Test
import kotlin.test.assertEquals

internal class VilkårsvurderingTest {
    @Test
    fun `Hovedregel er oppfylt`() {
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, listOf(hovedregelOppfylt, yrkesaktivFørForeldrepengerIkkeOppfylt, likestiltYtelseIkkeOppfylt).avgjørendeVilkårskode())
    }

    @Test
    fun `Hovedregel er ikke oppfylt`() {
        assertEquals(null, listOf(hovedregelIkkeOppfylt).avgjørendeVilkårskode())
    }

    @Test
    fun `Hovedregel ikke oppfylt, likestilt ytelse oppfylt, unntak ikke vurdert`() {
        assertEquals(null, listOf(hovedregelIkkeOppfylt, likestiltYtelseOppfylt).avgjørendeVilkårskode())
    }

    @Test
    fun `Hovedregel ikke oppfylt, likestilt ytelse oppfylt, unntak oppfylt`() {
        assertEquals(Vilkårskode.OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER, listOf(hovedregelIkkeOppfylt, likestiltYtelseOppfylt, yrkesaktivFørForeldrepengerOppfylt).avgjørendeVilkårskode())
    }

    @Test
    fun `Hovedregel ikke oppfylt, likestilt ytelse oppfylt, unntak ikke oppfylt`() {
        assertEquals(Vilkårskode.OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER, listOf(hovedregelIkkeOppfylt, likestiltYtelseOppfylt, yrkesaktivFørForeldrepengerIkkeOppfylt).avgjørendeVilkårskode())
    }

    @Test
    fun `Hovedregel ikke oppfylt, likestilt ytelse ikke oppfylt, unntak ikke vurdert`() {
        assertEquals(null, listOf(hovedregelIkkeOppfylt, likestiltYtelseIkkeOppfylt).avgjørendeVilkårskode())
    }

    val hovedregelOppfylt = nyVurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.Oppfylt)
    val hovedregelIkkeOppfylt = nyVurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.IkkeOppfylt)
    val likestiltYtelseOppfylt = nyVurdering(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE, Utfall.Oppfylt)
    val likestiltYtelseIkkeOppfylt = nyVurdering(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE, Utfall.IkkeOppfylt)
    val yrkesaktivFørForeldrepengerOppfylt = nyVurdering(Vilkårskode.OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER, Utfall.Oppfylt)
    val yrkesaktivFørForeldrepengerIkkeOppfylt = nyVurdering(Vilkårskode.OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER, Utfall.IkkeOppfylt)

    operator fun Vilkårsvurdering.plus(other: Vilkårsvurdering): List<Vilkårsvurdering> = listOf(this, other)

    private fun nyVurdering(
        vilkårskode: Vilkårskode,
        utfall: Utfall,
    ) = Vilkårsvurdering(
        id = VilkårsvurderingId.ny(),
        vilkårskode = vilkårskode,
        utfall = utfall,
        vurdertTidspunkt = null,
        kilde =
            Vurderingskilde.Automatisk(
                opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                grunnlag = Opptjeningsgrunnlag.Arbeidstaker(listOf(Arbeidsforhold("123456789", Periode(1.januar, 31.januar), Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT))),
                utledetFakta = UtledetFakta.Opptjeningstid(Periode(1.januar, 31.januar), 30),
                versjonAvKildekode = "En versjon",
            ),
    )
}
