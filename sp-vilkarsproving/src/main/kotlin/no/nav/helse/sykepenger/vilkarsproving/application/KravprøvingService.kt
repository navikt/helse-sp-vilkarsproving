package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.bootstrap.sikkerLogg
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import java.time.LocalDate

internal class KravprøvingService(
    kontekst: Transaksjonskontekst,
) {
    private val kravvurderingRepository = kontekst.kravvurderinger
    private val kravprøvingRepository = kontekst.kravprøvinger

    fun prøv(
        krav: Krav,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        startPrøving: () -> Kravprøving.Påbegynt,
    ): PrøvingResultat {
        kravvurderingRepository.gjeldende(krav, fødselsnummer, skjæringstidspunkt)?.let { vurdering ->
            sikkerLogg.info("Har allerede vurdering av $krav for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. KravvurderingId: ${vurdering.id}.")
            return PrøvingResultat.HarVurdering(vurdering)
        }

        kravprøvingRepository.finnSiste(krav, fødselsnummer, skjæringstidspunkt)?.takeUnless { it.erAvsluttet }?.let { pågående ->
            sikkerLogg.info("Prøving ${pågående.id} av $krav pågår allerede for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. Etterspør grunnlaget på nytt.")
            return PrøvingResultat.TrengerGrunnlag(pågående)
        }

        val (prøving, vurdering) = startPrøving()
        kravprøvingRepository.lagre(prøving)

        if (vurdering == null) {
            sikkerLogg.info("Startet prøving ${prøving.id} av $krav for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. Venter på ${prøving.uteståendeBehov}.")
            return PrøvingResultat.TrengerGrunnlag(prøving)
        }

        kravvurderingRepository.lagre(vurdering)
        sikkerLogg.info("Prøving ${prøving.id} av $krav fullført uten innhenting for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. KravvurderingId: ${vurdering.id}.")
        return PrøvingResultat.HarVurdering(vurdering)
    }

    sealed interface PrøvingResultat {
        data class HarVurdering(
            val vurdering: Kravvurdering,
        ) : PrøvingResultat

        data class TrengerGrunnlag(
            val prøving: Kravprøving,
        ) : PrøvingResultat
    }

    fun behandleGrunnlag(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        grunnlag: Vilkårsgrunnlag,
    ): GrunnlagResultat {
        val krav = grunnlag.krav
        val prøving = kravprøvingRepository.finnSiste(krav, fødselsnummer, skjæringstidspunkt)

        if (prøving == null) {
            sikkerLogg.error("Mottatt grunnlag for $krav for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt, men fant ingen prøving.")
            return GrunnlagResultat.IngenPrøvingFunnet
        }

        if (prøving.erAvsluttet) {
            sikkerLogg.info("Mottatt grunnlag for $krav for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt, men prøving ${prøving.id} er allerede avsluttet.")
            return GrunnlagResultat.AlleredeVurdert
        }

        val vurdering = prøving.motta(grunnlag)
        kravvurderingRepository.lagre(vurdering)
        kravprøvingRepository.lagre(prøving)
        sikkerLogg.info("Prøving ${prøving.id} av $krav fullført for fødselsnummer $fødselsnummer med skjæringstidspunkt $skjæringstidspunkt. KravvurderingId: ${vurdering.id}.")
        return GrunnlagResultat.NyVurderingForetatt(vurdering)
    }

    sealed interface GrunnlagResultat {
        data class NyVurderingForetatt(
            val vurdering: Kravvurdering.Vurdert,
        ) : GrunnlagResultat

        data object AlleredeVurdert : GrunnlagResultat

        data object IngenPrøvingFunnet : GrunnlagResultat
    }

    fun finnVurdering(
        krav: Krav,
        kravvurderingId: KravvurderingId,
        fødselsnummer: String,
    ): Kravvurdering = kravvurderingRepository.finn(krav, kravvurderingId) ?: error("Fant ikke vurdering av $krav med id $kravvurderingId")
}
