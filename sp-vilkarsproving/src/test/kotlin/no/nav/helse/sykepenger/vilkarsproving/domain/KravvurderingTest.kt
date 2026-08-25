package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.februar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

internal class KravvurderingTest {
    @Test
    fun `infotrygdvurdering har ingen sti, kun et utfall`() {
        val vurdering =
            Kravvurdering.fraInfotrygd(
                krav = Krav.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                utfall = Utfall.Oppfylt,
            )

        assertInstanceOf(Kravvurdering.OverførtFraInfotrygd::class.java, vurdering)
        assertEquals(Utfall.Oppfylt, vurdering.utfall)
        assertEquals(Krav.Opptjening, vurdering.krav)
    }

    @Test
    fun `saksbehandlervurdering har sti uten prøving`() {
        val ledd =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "vurdert etter dialog med bruker",
                vurdertTidspunkt = Instant.parse("2018-02-01T09:00:00Z"),
            )

        val vurdering =
            Kravvurdering.avSaksbehandler(
                krav = Krav.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            )

        assertEquals(Utfall.Oppfylt, vurdering.utfall)
        assertEquals(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE, vurdering.avgjørendeVilkårskode)
        val kilde = vurdering.vilkårsvurderinger.single().kilde
        assertInstanceOf(Vurderingskilde.Saksbehandler::class.java, kilde)
        assertEquals("Z999999", (kilde as Vurderingskilde.Saksbehandler).ident)
    }

    @Test
    fun `utfallet er det siste leddet i en flerleddet sti`() {
        val ikkeOppfylt =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "ikke sammenhengende arbeid",
                vurdertTidspunkt = null,
            )
        val avgjørende =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "hadde dagpenger i forkant",
                vurdertTidspunkt = null,
            )

        val vurdering =
            Kravvurdering.avSaksbehandler(
                krav = Krav.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ikkeOppfylt, avgjørende),
            )

        assertEquals(Utfall.Oppfylt, vurdering.utfall)
        assertEquals(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE, vurdering.avgjørendeVilkårskode)
        assertEquals(2, vurdering.vilkårsvurderinger.size)
    }

    @Test
    fun `en vurdert kravvurdering må ha minst ett ledd i stien`() {
        assertThrows<IllegalArgumentException> {
            Kravvurdering.avSaksbehandler(
                krav = Krav.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = emptyList(),
            )
        }
    }

    @Test
    fun `vurdertTidspunkt kan være null`() {
        val ledd =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "vurdert uten kjent tidspunkt",
                vurdertTidspunkt = null,
            )

        assertTrue(ledd.vurdertTidspunkt == null)
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
    }
}
