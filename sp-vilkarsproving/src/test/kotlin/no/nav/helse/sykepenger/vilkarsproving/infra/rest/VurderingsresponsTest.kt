package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.februar
import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertTrue

internal class VurderingsresponsTest {
    private val vurdertTidspunkt = Instant.parse("2024-02-01T12:00:00Z")

    @Test
    fun `arbeidstakerresponsen inneholder vurdert opptjeningsperiode og opptjeningsdager`() {
        val vurdering = automatiskArbeidstakervurdering(4.januar til 31.januar)

        val grunnlag = Vurderingsrespons.fra(vurdering).enesteVurdering().automatiskGrunnlag()

        assertEquals(4.januar, grunnlag.opptjeningsperiode?.fom)
        assertEquals(31.januar, grunnlag.opptjeningsperiode?.tom)
        assertEquals(28, grunnlag.opptjeningsdager)
    }

    @Test
    fun `skjaeringstidspunktet ligger paa rota, ikke per krav`() {
        val respons = Vurderingsrespons.fra(automatiskArbeidstakervurdering(4.januar til 31.januar))

        assertEquals(1.februar, respons.skjæringstidspunkt)
    }

    @Test
    fun `kravet peker paa vilkaaret som avgjorde det`() {
        val respons = Vurderingsrespons.fra(automatiskArbeidstakervurdering(4.januar til 31.januar))
        val krav = respons.krav.single() as ApiOpptjeningsvurdering.VurdertISpeil

        assertEquals(ApiKravkode.OPPTJENING, krav.kravkode)
        assertTrue(krav.rettTilSykepenger)
        assertEquals(ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, krav.avgjørendeVilkårskode)
        assertEquals(listOf(krav.avgjørendeVilkårskode), krav.vurderinger.map { it.vilkårskode })
    }

    @Test
    fun `vilkaarskode og utfall er to uavhengige akser`() {
        val forKortOpptjening = automatiskArbeidstakervurdering(29.januar til 31.januar)

        val vurdering = Vurderingsrespons.fra(forKortOpptjening).enesteVurdering()

        assertEquals(ApiUtfall.IKKE_OPPFYLT, vurdering.utfall)
        assertEquals(ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, vurdering.vilkårskode)
    }

    @Test
    fun `manuell vurdering har saksbehandler som kilde og ikke noe grunnlag`() {
        val ledd =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE,
                utfall = Utfall.Oppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "Mottok foreldrepenger fram til skjæringstidspunktet.",
                vurdertTidspunkt = vurdertTidspunkt,
            )
        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            )

        val api = Vurderingsrespons.fra(vurdering).enesteVurdering()
        val kilde = api.kilde as ApiVurderingskilde.Saksbehandler

        assertEquals(ApiVilkårskode.OPPTJENING_LIKESTILT_YTELSE, api.vilkårskode)
        assertEquals("Z999999", kilde.ident)
    }

    @Test
    fun `unntaksvilkaar er en helt vanlig vilkaarsvurdering i stien`() {
        val ledd =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "Ingen AAP forut for foreldrepengeperioden.",
                vurdertTidspunkt = vurdertTidspunkt,
            )
        val vurdering =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            )

        val api = Vurderingsrespons.fra(vurdering).enesteVurdering()

        assertEquals(ApiVilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP, api.vilkårskode)
        assertEquals(ApiUtfall.IKKE_OPPFYLT, api.utfall)
    }

    @Test
    fun `infotrygdvurdering blir et krav uten sti`() {
        val vurdering =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                girRettTilSykepenger = true,
            )

        val krav = Vurderingsrespons.fra(vurdering).krav.single()

        assertInstanceOf(ApiOpptjeningsvurdering.OverførtFraInfotrygd::class.java, krav)
        assertEquals(ApiKravkode.OPPTJENING, krav.kravkode)
        assertTrue(krav.rettTilSykepenger)
    }

    @Test
    fun `selvstendig naeringsdrivende gir grunnlag uten arbeidsforhold`() {
        val vurdering =
            Opptjeningsvurdering.automatisk(
                opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
                vurdertTidspunkt = vurdertTidspunkt,
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
            vurdertTidspunkt = vurdertTidspunkt,
        )
}

private fun ApiVilkårsvurderingerForPersonResponse.enesteVurdering() = (krav.single() as ApiOpptjeningsvurdering.VurdertISpeil).vurderinger.single()

private fun ApiVilkårsvurdering.automatiskGrunnlag() = (kilde as ApiVurderingskilde.Automatisk).grunnlag as ApiVurderingsgrunnlag.Arbeidsforhold
