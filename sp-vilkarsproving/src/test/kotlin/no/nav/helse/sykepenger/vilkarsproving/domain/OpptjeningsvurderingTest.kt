package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.til
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
                vilkårsvurdering = vilkårsvurdering,
            )

        // then
        assertTrue(vurdering.erOk)
        // avgjørendeVilkårskode()-regelen krever at hovedregelen (OPPTJENING_ARBEID_MINST_4_UKER) i det
        // hele tatt er blant vilkårsvurderingene før et unntak kan bli avgjørende. Her er den ikke det,
        // så det finnes ingen avgjørende vilkår selv om utfallet er oppfylt.
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, vurdering.avgjørendeVilkårskode)
        val kilde = vurdering.vilkårsvurderinger.single().kilde
        assertIs<Vurderingskilde.Saksbehandler>(kilde)
        assertEquals("Z999999", kilde.ident)
    }

    @Test
    fun `en infotrygdvurdering har ingen vilkårsvurderinger å videreføre`() {
        val forrige =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                erOk = false,
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurdering =
                    Vilkårsvurdering.avSaksbehandler(
                        vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                        utfall = Utfall.Oppfylt,
                        saksbehandlerIdent = "Z999999",
                        fritekstbegrunnelse = "hadde dagpenger i forkant",
                    ),
                forrigeVurdering = forrige,
            )

        assertEquals(1, vurdering.vilkårsvurderinger.size)
    }

    @Test
    fun `vurdertTidspunkt kan være null`() {
        val ledd =
            Vilkårsvurdering.overførtFraSpleis(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.Oppfylt,
                utledetFakta = UtledetFakta.Ingen,
                vurdertTidspunkt = null,
                grunnlag =
                    Opptjeningsgrunnlag.Arbeidstaker(
                        arbeidsforhold =
                            listOf(
                                Arbeidsforhold(
                                    orgnummer = "123456789",
                                    ansettelseperiode = 31.januar til 31.januar,
                                    type = Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT,
                                ),
                            ),
                    ),
            )

        assertEquals(null, ledd.vurdertTidspunkt)
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
    }
}
