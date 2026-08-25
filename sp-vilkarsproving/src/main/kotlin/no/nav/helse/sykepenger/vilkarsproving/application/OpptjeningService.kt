package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.application.KravprøvingService.GrunnlagResultat
import no.nav.helse.sykepenger.vilkarsproving.application.KravprøvingService.PrøvingResultat
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.HarVurdering
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.TrengerArbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import java.time.LocalDate

/**
 * Oversetter opptjeningsspesifikke kommandoer til den generelle prøvingsflyten.
 * All orkestrering ligger i [KravprøvingService]; her er kun det som er særegent for opptjening.
 *
 * Tjenesten konstrueres av en [Transaksjonskontekst] og lever like lenge som transaksjonen —
 * altså like lenge som behandlingen av én melding.
 */
internal class OpptjeningService(
    kontekst: Transaksjonskontekst,
) {
    private val kravprøving = KravprøvingService(kontekst)

    fun vurderOpptjening(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        arbeidssituasjon: Arbeidssituasjon,
    ): VurderOpptjeningResultat {
        // TODO: I fremtiden bør vi sjekke at eksisterende vurdering ble gjort på samme arbeidssituasjon,
        //  dersom situasjonen på et skjæringstidspunkt kan endre seg.
        val resultat =
            kravprøving.prøv(Krav.Opptjening, fødselsnummer, skjæringstidspunkt) {
                Opptjeningsprøving.start(fødselsnummer, skjæringstidspunkt, arbeidssituasjon)
            }
        return when (resultat) {
            is PrøvingResultat.HarVurdering -> HarVurdering(fødselsnummer, skjæringstidspunkt, resultat.vurdering.id)
            is PrøvingResultat.TrengerGrunnlag -> TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
        }
    }

    fun behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
        arbeidsforhold: List<Arbeidsforhold>,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): BehandleGrunnlagResultat {
        val resultat =
            kravprøving.behandleGrunnlag(
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                grunnlag = Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold),
            )
        return when (resultat) {
            is GrunnlagResultat.NyVurderingForetatt -> BehandleGrunnlagResultat.NyVurderingForetatt(fødselsnummer, skjæringstidspunkt, resultat.vurdering.id)
            GrunnlagResultat.AlleredeVurdert -> BehandleGrunnlagResultat.AlleredeVurdert
            GrunnlagResultat.IngenPrøvingFunnet -> BehandleGrunnlagResultat.IngenPrøvingFunnet
        }
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

    fun finnOpptjeningsvurdering(
        kravvurderingId: KravvurderingId,
        fødselsnummer: String,
    ): Kravvurdering =
        kravprøving.finnVurdering(
            krav = Krav.Opptjening,
            kravvurderingId = kravvurderingId,
            fødselsnummer = fødselsnummer,
        )
}
