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
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
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
        assertEquals(null, vurdering.avgjørendeVilkårskode)
        val kilde = vurdering.vilkårsvurderinger.single().kilde
        assertIs<Vurderingskilde.Saksbehandler>(kilde)
        assertEquals("Z999999", kilde.ident)
    }

    @Test
    fun `videreførte vilkårsvurderinger fra forrige vurdering blir med, men koder saksbehandleren overstyrer erstattes`() {
        // given
        val grunnlag =
            Opptjeningsgrunnlag.Arbeidstaker(
                arbeidsforhold =
                    listOf(
                        Arbeidsforhold(
                            orgnummer = "123456789",
                            ansettelseperiode = 31.januar til 31.januar,
                            type = Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT,
                        ),
                    ),
            )
        val automatiskIkkeOppfylt =
            Opptjeningsvurdering.automatisk(
                opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                grunnlag = grunnlag,
            )
        val gammelLikestiltYtelse =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z111111",
                fritekstbegrunnelse = "fant ingen likestilt ytelse",
            )

        val forrige = automatiskIkkeOppfylt.prøvPåNyttMed(gammelLikestiltYtelse)

        // when
        val nyLikestiltYtelseVurdering =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "hadde dagpenger i forkant",
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurdering = nyLikestiltYtelseVurdering,
                forrigeVurdering = forrige,
            )

        assertEquals(
            listOf(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Vilkårskode.OPPTJENING_LIKESTILT_YTELSE),
            vurdering.vilkårsvurderinger.map { it.vilkårskode },
        )
        assertEquals(Utfall.IkkeOppfylt, vurdering.vilkårsvurderinger.first().utfall)
        assertEquals(nyLikestiltYtelseVurdering.id, vurdering.vilkårsvurderinger.last().id)
        assertTrue(vurdering.erOk)
        // Hovedregelen er ikke oppfylt, og OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER er ikke blant
        // vilkårsvurderingene, så avgjørendeVilkårskode()-regelen gir null her selv om
        // OPPTJENING_LIKESTILT_YTELSE er oppfylt (se Vilkårsvurdering.avgjørendeVilkårskode()).
        assertEquals(null, vurdering.avgjørendeVilkårskode)
    }

    @Test
    fun `videreførte vilkårsvurderinger beholder id-en sin fordi raden gjenbrukes`() {
        // when
        val tidligere =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z111111",
                fritekstbegrunnelse = "for kort opptjening",
            )
        val forrige =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurdering = tidligere,
            )

        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurdering =
                    Vilkårsvurdering.avSaksbehandler(
                        vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                        utfall = Utfall.Oppfylt,
                        saksbehandlerIdent = "Z999999",
                        fritekstbegrunnelse = "hadde dagpenger i forkant",
                    ),
                forrigeVurdering = forrige,
            )

        val videreført = vurdering.vilkårsvurderinger.first()
        assertEquals(tidligere.id, videreført.id)
        assertEquals(tidligere.kilde, videreført.kilde)
        assertEquals(tidligere.utfall, videreført.utfall)
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
                        vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
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
