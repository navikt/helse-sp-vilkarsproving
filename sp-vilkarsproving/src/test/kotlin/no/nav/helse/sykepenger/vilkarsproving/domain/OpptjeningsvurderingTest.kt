package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.februar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

internal class OpptjeningsvurderingTest {
    @Test
    fun `infotrygdvurdering har ingen sti, kun et utfall`() {
        val vurdering =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                girRettTilSykepenger = true,
            )

        assertInstanceOf(Opptjeningsvurdering.OverførtFraInfotrygd::class.java, vurdering)
        assertTrue(vurdering.girRettTilSykepenger)
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
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            )

        assertTrue(vurdering.girRettTilSykepenger)
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
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ikkeOppfylt, avgjørende),
            )

        assertTrue(vurdering.girRettTilSykepenger)
        assertEquals(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE, vurdering.avgjørendeVilkårskode)
        assertEquals(2, vurdering.vilkårsvurderinger.size)
    }

    @Test
    fun `videreførte vilkårsvurderinger fra forrige vurdering blir med, men koder saksbehandleren overstyrer erstattes`() {
        val automatiskIkkeOppfylt =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z111111",
                fritekstbegrunnelse = "for kort opptjening",
                vurdertTidspunkt = null,
            )
        val gammelLikestiltYtelse =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z111111",
                fritekstbegrunnelse = "fant ingen likestilt ytelse",
                vurdertTidspunkt = null,
            )
        val forrige =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(automatiskIkkeOppfylt, gammelLikestiltYtelse),
            )

        val overstyring =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "hadde dagpenger i forkant",
                vurdertTidspunkt = null,
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(overstyring),
                forrigeVurdering = forrige,
            )

        assertEquals(
            listOf(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Vilkårskode.OPPTJENING_LIKESTILT_YTELSE),
            vurdering.vilkårsvurderinger.map { it.vilkårskode },
        )
        assertEquals(Utfall.IkkeOppfylt, vurdering.vilkårsvurderinger.first().utfall)
        assertEquals(overstyring.id, vurdering.vilkårsvurderinger.last().id)
        assertTrue(vurdering.girRettTilSykepenger)
        assertEquals(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE, vurdering.avgjørendeVilkårskode)
    }

    @Test
    fun `videreførte vilkårsvurderinger får nye id-er fordi de lagres som nye rader`() {
        val tidligere =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z111111",
                fritekstbegrunnelse = "for kort opptjening",
                vurdertTidspunkt = null,
            )
        val forrige =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(tidligere),
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti =
                    listOf(
                        Vilkårsvurdering.avSaksbehandler(
                            vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                            utfall = Utfall.Oppfylt,
                            saksbehandlerIdent = "Z999999",
                            fritekstbegrunnelse = "hadde dagpenger i forkant",
                            vurdertTidspunkt = null,
                        ),
                    ),
                forrigeVurdering = forrige,
            )

        val videreført = vurdering.vilkårsvurderinger.first()
        assertNotEquals(tidligere.id, videreført.id)
        assertEquals(tidligere.kilde, videreført.kilde)
        assertEquals(tidligere.utfall, videreført.utfall)
    }

    @Test
    fun `en infotrygdvurdering har ingen sti å videreføre`() {
        val forrige =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                girRettTilSykepenger = false,
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti =
                    listOf(
                        Vilkårsvurdering.avSaksbehandler(
                            vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                            utfall = Utfall.Oppfylt,
                            saksbehandlerIdent = "Z999999",
                            fritekstbegrunnelse = "hadde dagpenger i forkant",
                            vurdertTidspunkt = null,
                        ),
                    ),
                forrigeVurdering = forrige,
            )

        assertEquals(1, vurdering.vilkårsvurderinger.size)
    }

    @Test
    fun `en vurdert opptjeningsvurdering må ha minst ett ledd i stien`() {
        assertThrows<IllegalArgumentException> {
            Opptjeningsvurdering.avSaksbehandler(
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
