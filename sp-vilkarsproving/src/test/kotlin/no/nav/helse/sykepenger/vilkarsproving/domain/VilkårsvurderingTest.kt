package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.avgjørendeVilkårsvurdering
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `hvert punktum i andre ledd har sin egen lovreferanse`() {
        assertEquals(
            listOf(2 to 1, 2 to 2, 2 to 3),
            Vilkårskode.andreAvsnitt.map { it.lovreferanse.avsnitt to it.lovreferanse.setning },
        )
        assertEquals(1.januar(1997), Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER.lovreferanse.iKraftFra)
        assertEquals(1.januar(2018), Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP.lovreferanse.iKraftFra)
        assertEquals(1.januar(2018), Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER.lovreferanse.iKraftFra)
    }

    // Scenario A–E fra docs/opptjening-8-2-kodeverk.md, med vilkårskodene fra regler.js

    @Test
    fun `A - hovedregelen er avgjørende når den er oppfylt, også når andre ledd er vurdert`() {
        val vurderinger = listOf(hovedregelOppfylt, nyVurdering(L2P1, Utfall.Oppfylt))
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, vurderinger.avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    @Test
    fun `B - første punktum er avgjørende når hovedregelen ikke er oppfylt og det ikke er foreldrepenger`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.Oppfylt))
        assertEquals(L2P1, vurderinger.avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    @Test
    fun `B - første punktum er avgjørende når foreldrepengene ikke er opptjent på AAP`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.Oppfylt), nyVurdering(L2P2, Utfall.Oppfylt))
        assertEquals(L2P1, vurderinger.avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    @Test
    fun `B - første punktum kan avgjøre uten at hovedregelen er vurdert`() {
        val vurderinger = listOf(nyVurdering(L2P1, Utfall.Oppfylt))
        assertEquals(L2P1, vurderinger.avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    @Test
    fun `C - tredje punktum er avgjørende når foreldrepenger opptjent på AAP reddes av yrkesaktivitet før uttak`() {
        val vurderinger =
            listOf(
                hovedregelIkkeOppfylt,
                nyVurdering(L2P1, Utfall.Oppfylt),
                nyVurdering(L2P2, Utfall.IkkeOppfylt),
                nyVurdering(L2P3, Utfall.Oppfylt),
            )
        assertEquals(L2P3, vurderinger.avgjørendeVilkårsvurdering()?.vilkårskode)
    }

    @Test
    fun `D - ingen avgjørende når verken hovedregel eller første punktum er oppfylt`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.IkkeOppfylt))
        assertNull(vurderinger.avgjørendeVilkårsvurdering())
    }

    @Test
    fun `E - ingen avgjørende når foreldrepengene er opptjent på AAP uten yrkesaktivitet før uttak`() {
        val vurderinger =
            listOf(
                hovedregelIkkeOppfylt,
                nyVurdering(L2P1, Utfall.Oppfylt),
                nyVurdering(L2P2, Utfall.IkkeOppfylt),
                nyVurdering(L2P3, Utfall.IkkeOppfylt),
            )
        assertNull(vurderinger.avgjørendeVilkårsvurdering())
    }

    @Test
    fun `første punktum avgjør ikke når andre punktum slår inn og tredje punktum ikke er vurdert ennå`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.Oppfylt), nyVurdering(L2P2, Utfall.IkkeOppfylt))
        assertNull(vurderinger.avgjørendeVilkårsvurdering())
    }

    private val L2P1 = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER
    private val L2P2 = Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP
    private val L2P3 = Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER

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
        vurdertTidspunkt = Instant.now(),
        kilde =
            Vurderingskilde.Automatisk(
                opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                grunnlag = Opptjeningsgrunnlag.Arbeidstaker(listOf(Arbeidsforhold("123456789", Periode(1.januar, 31.januar), Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT))),
                utledetFakta = UtledetFakta.Opptjeningstid(Periode(1.januar, 31.januar), 30),
                versjonAvKildekode = "En versjon",
            ),
        vurderingskilde = Opptjeningsvurderingskilde.VURDERT_I_SP_VILKARSPROVING,
        lovreferanse = vilkårskode.lovreferanse,
    )
}
