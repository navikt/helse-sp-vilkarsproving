package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.februar
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class OpptjeningsvurderingTest {
    @Test
    fun `infotrygdvurdering har ingen vilkårsvurderinger, kun et utfall`() {
        // given
        val vurdering =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                erOk = true,
                vurdertTidspunkt = Instant.now(),
            )

        // then
        assertIs<Opptjeningsvurdering.OverførtFraInfotrygd>(vurdering)
        assertTrue(vurdering.erOk)
    }

    @Test
    fun `saksbehandlervurdering har vilkårsvurderinger uten prøving`() {
        // given
        val vilkårsvurdering =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "vurdert etter dialog med bruker",
            )

        // when
        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurderinger = listOf(vilkårsvurdering),
            )

        // then
        assertTrue(vurdering.erOk)
        val kilde = vurdering.vilkårsvurderinger.single().kilde
        assertIs<Vurderingskilde.Saksbehandler>(kilde)
        assertEquals("Z999999", kilde.ident)
    }

    @Test
    fun `saksbehandlervurdering erstatter alle tidligere vilkårsvurderinger`() {
        val forrige =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurderinger =
                    listOf(
                        saksbehandlervurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.IkkeOppfylt),
                        saksbehandlervurdering(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER, Utfall.Oppfylt),
                    ),
            )
        val ny = saksbehandlervurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.Oppfylt)

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurderinger = listOf(ny),
                forrigeVurdering = forrige,
            )

        assertEquals(listOf(ny), vurdering.vilkårsvurderinger)
    }

    @Test
    fun `manuell vurdering viderefører kategori fra forrige vurdering`() {
        val forrige =
            checkNotNull(
                Opptjeningsprøving
                    .start(
                        fødselsnummer = FØDSELSNUMMER,
                        skjæringstidspunkt = 1.februar,
                        arbeidssituasjon = Arbeidssituasjon.SelvstendigNæringsdrivende,
                    ).vurdering,
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurderinger =
                    listOf(
                        Vilkårsvurdering.avSaksbehandler(
                            vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                            utfall = Utfall.IkkeOppfylt,
                            saksbehandlerIdent = "Z999999",
                            fritekstbegrunnelse = "ny vurdering",
                        ),
                    ),
                forrigeVurdering = forrige,
            )

        assertEquals(Kategori.SelvstendigNæringsdrivende, vurdering.kategori)
    }

    private fun saksbehandlervurdering(
        vilkårskode: Vilkårskode,
        utfall: Utfall,
    ) = Vilkårsvurdering.avSaksbehandler(vilkårskode, utfall, "Z999999", "begrunnelse")

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
    }
}
