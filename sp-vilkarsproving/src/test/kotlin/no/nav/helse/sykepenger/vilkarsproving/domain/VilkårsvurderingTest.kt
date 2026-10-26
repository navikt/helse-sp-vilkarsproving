package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.opptjeningErOppfylt
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VilkårsvurderingTest {
    @Test
    fun `Hovedregel er oppfylt`() {
        val vurderinger = listOf(hovedregelOppfylt)
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `Hovedregel er ikke oppfylt`() {
        assertFalse(listOf(hovedregelIkkeOppfylt).opptjeningErOppfylt())
    }

    @Test
    fun `hvert punktum i andre ledd har sin egen lovreferanse`() {
        assertEquals(
            listOf(2 to 1, 2 to 2, 2 to 3),
            listOf(L2P1, L2P2, L2P3).map { it.lovreferanse.avsnitt to it.lovreferanse.setning },
        )
        assertEquals(1.januar(1997), Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER.lovreferanse.iKraftFra)
        assertEquals(1.januar(2018), Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP.lovreferanse.iKraftFra)
        assertEquals(1.januar(2018), Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER.lovreferanse.iKraftFra)
    }

    // Scenario A–E fra docs/opptjening-8-2-kodeverk.md, med vilkårskodene fra regler.js

    @Test
    fun `A - oppfylt når hovedregelen er oppfylt, også når andre ledd er vurdert`() {
        val vurderinger = listOf(hovedregelOppfylt, nyVurdering(L2P1, Utfall.Oppfylt))
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `B - oppfylt etter første punktum når hovedregelen ikke er oppfylt og det ikke er foreldrepenger`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.Oppfylt))
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `B - oppfylt etter første punktum når foreldrepengene ikke er opptjent på AAP`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.Oppfylt), nyVurdering(L2P2, Utfall.Oppfylt))
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `B - oppfylt etter første punktum uten at hovedregelen er vurdert`() {
        val vurderinger = listOf(nyVurdering(L2P1, Utfall.Oppfylt))
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `C - oppfylt etter tredje punktum når foreldrepenger opptjent på AAP reddes av yrkesaktivitet før uttak`() {
        val vurderinger =
            listOf(
                hovedregelIkkeOppfylt,
                nyVurdering(L2P1, Utfall.Oppfylt),
                nyVurdering(L2P2, Utfall.IkkeOppfylt),
                nyVurdering(L2P3, Utfall.Oppfylt),
            )
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `D - ikke oppfylt når verken hovedregel eller første punktum er oppfylt`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.IkkeOppfylt))
        assertFalse(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `E - ikke oppfylt når foreldrepengene er opptjent på AAP uten yrkesaktivitet før uttak`() {
        val vurderinger =
            listOf(
                hovedregelIkkeOppfylt,
                nyVurdering(L2P1, Utfall.Oppfylt),
                nyVurdering(L2P2, Utfall.IkkeOppfylt),
                nyVurdering(L2P3, Utfall.IkkeOppfylt),
            )
        assertFalse(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `ikke oppfylt når andre punktum slår inn og tredje punktum ikke er vurdert ennå`() {
        val vurderinger = listOf(hovedregelIkkeOppfylt, nyVurdering(L2P1, Utfall.Oppfylt), nyVurdering(L2P2, Utfall.IkkeOppfylt))
        assertFalse(vurderinger.opptjeningErOppfylt())
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
