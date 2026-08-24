package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.application.VilkårsprøvingService.GrunnlagResultat
import no.nav.helse.sykepenger.vilkarsproving.application.VilkårsprøvingService.PrøvingResultat
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.HarVurdering
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.TrengerArbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import java.time.LocalDate

/**
 * Oversetter opptjeningsspesifikke kommandoer til den generelle prøvingsflyten.
 * All orkestrering ligger i [VilkårsprøvingService]; her er kun det som er særegent for opptjening.
 *
 * Tjenesten konstrueres av en [Transaksjonskontekst] og lever like lenge som transaksjonen —
 * altså like lenge som behandlingen av én melding.
 */
internal class OpptjeningService(
    kontekst: Transaksjonskontekst,
) {
    private val vilkårsprøving = VilkårsprøvingService(kontekst)

    fun vurderOpptjening(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        arbeidssituasjon: Arbeidssituasjon,
    ): VurderOpptjeningResultat {
        // TODO: I fremtiden bør vi sjekke at eksisterende vurdering ble gjort på samme arbeidssituasjon,
        //  dersom situasjonen på et skjæringstidspunkt kan endre seg.
        val resultat =
            vilkårsprøving.prøv(Vilkår.Opptjening, fødselsnummer, skjæringstidspunkt) {
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
            vilkårsprøving.behandleGrunnlag(
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
            val vurderingId: VurderingId,
        ) : BehandleGrunnlagResultat()

        data object AlleredeVurdert : BehandleGrunnlagResultat()

        data object IngenPrøvingFunnet : BehandleGrunnlagResultat()
    }

    fun finnOpptjeningsvurdering(
        vurderingId: VurderingId,
        fødselsnummer: String,
    ): Vilkårsvurdering =
        vilkårsprøving.finnVurdering(
            vilkår = Vilkår.Opptjening,
            vurderingId = vurderingId,
            fødselsnummer = fødselsnummer,
        )
}
