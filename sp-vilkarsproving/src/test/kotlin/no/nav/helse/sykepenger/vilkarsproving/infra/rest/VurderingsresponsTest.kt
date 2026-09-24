package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.Periode
import no.nav.helse.desember
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.*
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.til
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

internal class VurderingsresponsTest {
    @Test
    fun `arbeidstakerresponsen inneholder vurdert opptjeningsperiode og opptjeningsdager`() {
        val vurdering = automatiskArbeidstakervurdering(4.januar til 31.januar)

        val grunnlag = Vurderingsrespons.fra(vurdering).enesteVurdering().automatiskGrunnlag()

        assertEquals(4.januar, grunnlag.opptjeningsperiode?.fom)
        assertEquals(31.januar, grunnlag.opptjeningsperiode?.tom)
        assertEquals(28, grunnlag.opptjeningsdager)
    }

    @Test
    fun `skjaeringstidspunktet ligger på rota, ikke per opptjeningsvurdering`() {
        val respons = Vurderingsrespons.fra(automatiskArbeidstakervurdering(4.januar til 31.januar))

        assertEquals(1.februar, respons.skjæringstidspunkt)
    }

    @Test
    fun `opptjeningsvurderingen peker på vilkaaret som avgjorde det`() {
        val respons = Vurderingsrespons.fra(automatiskArbeidstakervurdering(4.januar til 31.januar))
        val krav = respons.krav.single() as ApiOpptjeningsvurdering.VurdertISpeil

        assertEquals(ApiKravkode.OPPTJENING, krav.kravkode)
        assertTrue(krav.opptjeningOk)
        assertEquals(ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, krav.avgjørendeVilkårskode)
        assertEquals(listOf(krav.avgjørendeVilkårskode), krav.vurderinger.map { it.vilkårskode })
    }

    @Test
    fun `vilkaarskode og utfall er to uavhengige akser`() {
        val forKortOpptjening = automatiskArbeidstakervurdering(29.januar til 31.januar)

        val vurdering = Vurderingsrespons.fra(forKortOpptjening).enesteVurdering()

        assertEquals(ApiUtfall.IKKE_OPPFYLT, vurdering.utfall)
        assertEquals(ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, vurdering.vilkårskode)
        assertEquals(ApiLovreferanse("folketrygdloven", "8-2", 1, 1, null, 22.desember(2025)), vurdering.lovreferanse)
    }

    @Test
    fun `manuell vurdering har saksbehandler som kilde og ikke noe grunnlag`() {
        val vilkårsvurdering =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "Mottok foreldrepenger fram til skjæringstidspunktet.",
                journalpostId = listOf("journalpost-1", "journalpost-2"),
            )
        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                vilkårsvurdering = vilkårsvurdering,
            )

        val api = Vurderingsrespons.fra(vurdering).enesteVurdering()
        val kilde = api.kilde as ApiVurderingskilde.Saksbehandler

        assertEquals(ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, api.vilkårskode)
        assertEquals(ApiLovreferanse("folketrygdloven", "8-2", 1, 1, null, 22.desember(2025)), api.lovreferanse)
        assertEquals("Z999999", kilde.ident)
        assertEquals(listOf("journalpost-1", "journalpost-2"), kilde.journalpostId)
    }

    @Test
    fun `unntaksvilkår er en helt vanlig vilkårsvurdering`() {
        val vilkårsvurdering =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "Ingen AAP forut for foreldrepengeperioden.",
            )
        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                vilkårsvurdering = vilkårsvurdering,
            )

        val api = Vurderingsrespons.fra(vurdering).enesteVurdering()

        assertEquals(ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, api.vilkårskode)
        assertEquals(ApiUtfall.IKKE_OPPFYLT, api.utfall)
    }

    @Test
    fun `infotrygdvurdering blir en opptjeningsvurdering uten enkeltvurderinger`() {
        val vurdering =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                erOk = true,
            )

        val krav = Vurderingsrespons.fra(vurdering).krav.single()

        assertInstanceOf(ApiOpptjeningsvurdering.OverførtFraInfotrygd::class.java, krav)
        assertEquals(ApiKravkode.OPPTJENING, krav.kravkode)
        assertTrue(krav.opptjeningOk)
    }

    @Test
    fun `selvstendig naeringsdrivende gir grunnlag uten arbeidsforhold`() {
        val vurdering =
            Opptjeningsvurdering.automatisk(
                opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
            )

        val kilde = Vurderingsrespons.fra(vurdering).enesteVurdering().kilde as ApiVurderingskilde.Automatisk

        assertEquals(ApiVurderingsgrunnlag.SelvstendigNæringsdrivende(), kilde.grunnlag)
    }

    private fun automatiskArbeidstakervurdering(ansettelseperiode: Periode) =
        Opptjeningsvurdering.automatisk(
            opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
            fødselsnummer = "12345678901",
            skjæringstidspunkt = 1.februar,
            grunnlag =
                Opptjeningsgrunnlag.Arbeidstaker(
                    listOf(Arbeidsforhold(orgnummer = "987654321", ansettelseperiode = ansettelseperiode, type = ORDINÆRT)),
                ),
        )
}

private fun ApiVilkårsvurderingerForPersonResponse.enesteVurdering() = (krav.single() as ApiOpptjeningsvurdering.VurdertISpeil).vurderinger.single()

private fun ApiVilkårsvurdering.automatiskGrunnlag() = (kilde as ApiVurderingskilde.Automatisk).grunnlag as ApiVurderingsgrunnlag.Arbeidsforhold
