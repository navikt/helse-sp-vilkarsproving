package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.HarVurdering
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.TrengerArbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.sikkerLogg
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import java.time.LocalDate

/**
 * Orkestrerer prøvingen av opptjeningskravet: starter prøvinger, tar imot grunnlaget de venter på,
 * og henter fram tidligere vurderinger.
 *
 * Tjenesten konstrueres av en [Transaksjonskontekst] og lever like lenge som transaksjonen —
 * altså like lenge som behandlingen av én melding.
 */
internal class OpptjeningService(
    kontekst: Transaksjonskontekst,
) {
    private val kravvurderingRepository = kontekst.kravvurderinger
    private val opptjeningsprøvingRepository = kontekst.opptjeningsprøvinger

    fun vurderOpptjening(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        arbeidssituasjon: Arbeidssituasjon,
    ): VurderOpptjeningResultat {
        // TODO: I fremtiden bør vi sjekke at eksisterende vurdering ble gjort på samme arbeidssituasjon,
        //  dersom situasjonen på et skjæringstidspunkt kan endre seg.
        kravvurderingRepository.gjeldende(Krav.Opptjening, fødselsnummer, skjæringstidspunkt)?.let { vurdering ->
            sikkerLogg.info("Har allerede opptjeningsvurdering for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. KravvurderingId: ${vurdering.id}.")
            return HarVurdering(fødselsnummer, skjæringstidspunkt, vurdering.id)
        }

        opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt)?.takeUnless { it.erAvsluttet }?.let {
            sikkerLogg.info("Opptjeningsprøving ${it.id} pågår allerede for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. Etterspør grunnlaget på nytt.")
            return TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
        }

        val (prøving, vurdering) = Opptjeningsprøving.start(fødselsnummer, skjæringstidspunkt, arbeidssituasjon)
        opptjeningsprøvingRepository.lagre(prøving)

        if (vurdering == null) {
            sikkerLogg.info("Startet opptjeningsprøving ${prøving.id} for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. Venter på ${prøving.uteståendeBehov}.")
            return TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
        }

        kravvurderingRepository.lagre(vurdering)
        sikkerLogg.info("Opptjeningsprøving ${prøving.id} fullført uten innhenting for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. KravvurderingId: ${vurdering.id}.")
        return HarVurdering(fødselsnummer, skjæringstidspunkt, vurdering.id)
    }

    fun behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
        arbeidsforhold: List<Arbeidsforhold>,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): BehandleGrunnlagResultat {
        val prøving = opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt)

        if (prøving == null) {
            sikkerLogg.error("Mottatt grunnlag for opptjening for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt, men fant ingen prøving.")
            return BehandleGrunnlagResultat.IngenPrøvingFunnet
        }

        if (prøving.erAvsluttet) {
            sikkerLogg.info("Mottatt grunnlag for opptjening for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt, men prøving ${prøving.id} er allerede avsluttet.")
            return BehandleGrunnlagResultat.AlleredeVurdert
        }

        val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold))
        kravvurderingRepository.lagre(vurdering)
        opptjeningsprøvingRepository.lagre(prøving)
        sikkerLogg.info("Opptjeningsprøving ${prøving.id} fullført for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. KravvurderingId: ${vurdering.id}.")
        return BehandleGrunnlagResultat.NyVurderingForetatt(fødselsnummer, skjæringstidspunkt, vurdering.id)
    }

    sealed class BehandleGrunnlagResultat {
        data class NyVurderingForetatt(
            val fødselsnummer: String,
            val skjæringstidspunkt: LocalDate,
            val kravvurderingId: KravvurderingId,
        ) : BehandleGrunnlagResultat()

        data object AlleredeVurdert : BehandleGrunnlagResultat()

        data object IngenPrøvingFunnet : BehandleGrunnlagResultat()
    }

    fun finnOpptjeningsvurdering(kravvurderingId: KravvurderingId): Kravvurdering =
        kravvurderingRepository.finn(Krav.Opptjening, kravvurderingId)
            ?: error("Fant ikke opptjeningsvurdering med id $kravvurderingId")
}
