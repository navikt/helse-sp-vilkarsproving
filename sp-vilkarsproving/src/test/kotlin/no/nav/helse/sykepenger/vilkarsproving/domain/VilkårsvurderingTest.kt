package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.avgjørendeVilkårsvurdering
import kotlin.test.Test
import kotlin.test.assertEquals

internal class VilkårsvurderingTest {
    @Test
    fun `Hovedregel er oppfylt`() {
        val vurderinger = listOf(hovedregelOppfylt)
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, vurderinger.avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    @Test
    fun `Hovedregel er ikke oppfylt`() {
        assertEquals(null, listOf(hovedregelIkkeOppfylt).avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    val hovedregelOppfylt = nyVurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.Oppfylt)
    val hovedregelIkkeOppfylt = nyVurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.IkkeOppfylt)

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
        lovreferanse = Lovreferanse.`§ 8-2 første avsnitt, første setning`(),
    )
}
