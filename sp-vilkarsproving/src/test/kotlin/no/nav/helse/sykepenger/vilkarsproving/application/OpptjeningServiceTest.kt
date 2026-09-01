package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.Periode
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService.BehandleGrunnlagResultat
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.HarVurdering
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.TrengerArbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Grunnlagsbehov
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import no.nav.helse.til
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

internal class OpptjeningServiceTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val vurderinger = transaksjon.opptjeningsvurderinger
    private val prøvinger = transaksjon.opptjeningsprøvinger
    private val service = OpptjeningService(transaksjon)

    @Test
    fun `arbeidstaker uten eksisterende vurdering starter en prøving som venter på arbeidsforhold`() {
        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        assertEquals(TrengerArbeidsforhold(FØDSELSNUMMER, 1.februar), resultat)
        assertEquals(0, vurderinger.antallLagringer)

        val prøving = prøvinger.allePrøvinger.single()
        assertEquals(FØDSELSNUMMER, prøving.fødselsnummer)
        assertEquals(1.februar, prøving.skjæringstidspunkt)
        assertFalse(prøving.erAvsluttet)
        assertEquals(Grunnlagsbehov.Arbeidsforhold, prøving.uteståendeBehov)
    }

    @Test
    fun `selvstendig næringsdrivende vurderes umiddelbart`() {
        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)

        val harVurdering = assertInstanceOf(HarVurdering::class.java, resultat)
        assertEquals(FØDSELSNUMMER, harVurdering.fødselsnummer)
        assertEquals(1.februar, harVurdering.skjæringstidspunkt)

        val vurdering = vurderinger.finn(harVurdering.opptjeningsvurderingId) as Opptjeningsvurdering.VurdertISpeil
        val ledd = vurdering.vilkårsvurderinger.single()
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, ledd.vilkårskode)
        assertEquals(Utfall.Oppfylt, ledd.utfall)
        assertEquals(Opptjeningsgrunnlag.SelvstendigNæringsdrivende, (ledd.kilde as Vurderingskilde.Automatisk).grunnlag)
        assertTrue(prøvinger.allePrøvinger.single().erAvsluttet)
    }

    @Test
    fun `eksisterende vurdering gjenbrukes`() {
        val eksisterende = fullførtPrøving(1.februar)

        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        assertEquals(HarVurdering(FØDSELSNUMMER, 1.februar, eksisterende), resultat)
        assertEquals(1, vurderinger.antallLagringer)
        assertEquals(1, prøvinger.allePrøvinger.size)
    }

    @Test
    fun `eksisterende vurdering gjenbrukes også for selvstendig næringsdrivende`() {
        val eksisterende = fullførtPrøving(1.februar)

        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)

        assertEquals(HarVurdering(FØDSELSNUMMER, 1.februar, eksisterende), resultat)
        assertEquals(1, prøvinger.allePrøvinger.size)
    }

    @Test
    fun `pågående prøving fører til nytt behov om arbeidsforhold, ikke ny prøving`() {
        service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        assertEquals(TrengerArbeidsforhold(FØDSELSNUMMER, 1.februar), resultat)
        assertEquals(1, prøvinger.allePrøvinger.size)
    }

    @Test
    fun `vurdering på et annet skjæringstidspunkt gjenbrukes ikke`() {
        fullførtPrøving(1.januar)

        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        assertEquals(TrengerArbeidsforhold(FØDSELSNUMMER, 1.februar), resultat)
    }

    @Test
    fun `vurdering for en annen person gjenbrukes ikke`() {
        fullførtPrøving(1.februar, fødselsnummer = ET_ANNET_FØDSELSNUMMER)

        val resultat = service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        assertEquals(TrengerArbeidsforhold(FØDSELSNUMMER, 1.februar), resultat)
    }

    @Test
    fun `grunnlag uten påbegynt prøving gir ingen prøving funnet`() {
        val resultat =
            service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
                arbeidsforhold = listOf(arbeidsforhold(1.januar til 31.januar)),
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
            )

        assertEquals(BehandleGrunnlagResultat.IngenPrøvingFunnet, resultat)
        assertEquals(0, vurderinger.antallLagringer)
    }

    @Test
    fun `grunnlag fullfører påbegynt prøving og produserer vurderingen`() {
        service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)
        val arbeidsforhold = listOf(arbeidsforhold(1.januar til 31.januar))

        val resultat =
            service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
                arbeidsforhold = arbeidsforhold,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
            )

        val nyVurdering = assertInstanceOf(BehandleGrunnlagResultat.NyVurderingForetatt::class.java, resultat)
        val prøving = prøvinger.allePrøvinger.single()
        assertEquals(Opptjeningsprøving.Tilstand.Fullført(nyVurdering.opptjeningsvurderingId), prøving.tilstand)

        val vurdering = vurderinger.finn(nyVurdering.opptjeningsvurderingId) as Opptjeningsvurdering.VurdertISpeil
        val ledd = vurdering.vilkårsvurderinger.single()
        val kilde = ledd.kilde as Vurderingskilde.Automatisk
        assertEquals(prøving.id, kilde.opptjeningsprøvingId)
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, ledd.vilkårskode)
        assertEquals(Utfall.Oppfylt, ledd.utfall)
        assertEquals(arbeidsforhold, (kilde.grunnlag as Opptjeningsgrunnlag.Arbeidstaker).arbeidsforhold)
    }

    @Test
    fun `grunnlag med for kort opptjening gir ikke oppfylt`() {
        service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
            arbeidsforhold = listOf(arbeidsforhold(5.januar til 31.januar)),
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
        )

        val ledd = (vurderinger.alleVurderinger.single() as Opptjeningsvurdering.VurdertISpeil).vilkårsvurderinger.single()
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, ledd.vilkårskode)
        assertEquals(Utfall.IkkeOppfylt, ledd.utfall)
    }

    @Test
    fun `grunnlag uten arbeidsforhold fullfører prøvingen`() {
        service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        val resultat =
            service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
                arbeidsforhold = emptyList(),
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
            )

        assertInstanceOf(BehandleGrunnlagResultat.NyVurderingForetatt::class.java, resultat)
        assertTrue(prøvinger.allePrøvinger.single().erAvsluttet)
        val ledd = (vurderinger.alleVurderinger.single() as Opptjeningsvurdering.VurdertISpeil).vilkårsvurderinger.single()
        assertEquals(Utfall.IkkeOppfylt, ledd.utfall)
    }

    @Test
    fun `grunnlag på allerede fullført prøving gjør ingenting`() {
        service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)
        service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
            arbeidsforhold = listOf(arbeidsforhold(1.januar til 31.januar)),
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
        )
        val opprinneligVurdering = vurderinger.alleVurderinger.single()

        val resultat =
            service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
                arbeidsforhold = listOf(arbeidsforhold(5.januar til 31.januar)),
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
            )

        assertEquals(BehandleGrunnlagResultat.AlleredeVurdert, resultat)
        assertSame(opprinneligVurdering, vurderinger.alleVurderinger.single())
    }

    @Test
    fun `grunnlag for et annet skjæringstidspunkt treffer ikke prøvingen`() {
        service.vurderOpptjening(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker)

        val resultat =
            service.behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
                arbeidsforhold = listOf(arbeidsforhold(1.januar til 31.januar)),
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.mars,
            )

        assertEquals(BehandleGrunnlagResultat.IngenPrøvingFunnet, resultat)
        assertFalse(prøvinger.allePrøvinger.single().erAvsluttet)
        assertEquals(0, vurderinger.antallLagringer)
    }

    @Test
    fun `finner lagret opptjeningsvurdering`() {
        val opptjeningsvurderingId = fullførtPrøving(1.februar)

        assertEquals(opptjeningsvurderingId, service.finnOpptjeningsvurdering(opptjeningsvurderingId).id)
    }

    @Test
    fun `ukjent opptjeningsvurdering gir feil`() {
        val ukjentId = OpptjeningsvurderingId.ny()

        val feil = assertThrows<IllegalStateException> { service.finnOpptjeningsvurdering(ukjentId) }
        assertEquals("Fant ikke opptjeningsvurdering med id $ukjentId", feil.message)
    }

    private fun fullførtPrøving(
        skjæringstidspunkt: LocalDate,
        fødselsnummer: String = FØDSELSNUMMER,
    ): OpptjeningsvurderingId {
        val prøving = Opptjeningsprøving.start(fødselsnummer, skjæringstidspunkt, Arbeidssituasjon.Arbeidstaker).prøving
        val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(listOf(arbeidsforhold(1.januar til 31.januar))))
        prøvinger.lagre(prøving)
        vurderinger.lagre(vurdering)
        return vurdering.id
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ET_ANNET_FØDSELSNUMMER = "12029240046"
        const val ORGNUMMER = "987654321"

        fun arbeidsforhold(
            ansettelseperiode: Periode,
            orgnummer: String = ORGNUMMER,
        ) = Arbeidsforhold(orgnummer = orgnummer, ansettelseperiode = ansettelseperiode, type = ORDINÆRT)
    }
}
