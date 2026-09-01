package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.speil.backend.app.logging.loggError
import no.nav.helse.speil.backend.app.logging.loggInfo
import no.nav.helse.speil.backend.app.logging.medMdc
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.HarVurdering
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.TrengerArbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import java.time.LocalDate

internal class OpptjeningService(
    kontekst: Transaksjonskontekst,
) {
    private val kravvurderingRepository = kontekst.opptjeningsvurderinger
    private val opptjeningsprøvingRepository = kontekst.opptjeningsprøvinger

    fun vurderOpptjening(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        arbeidssituasjon: Arbeidssituasjon,
    ): VurderOpptjeningResultat =
        medMdc(
            OpptjeningMdcKeys.FØDSELSNUMMER to fødselsnummer,
            OpptjeningMdcKeys.SKJÆRINGSTIDSPUNKT to skjæringstidspunkt.toString(),
        ) {
            // TODO: I fremtiden bør vi sjekke at eksisterende vurdering ble gjort på samme arbeidssituasjon,
            //  dersom situasjonen på et skjæringstidspunkt kan endre seg.
            kravvurderingRepository.gjeldende(fødselsnummer, skjæringstidspunkt)?.let { vurdering ->
                loggInfo(
                    "Har allerede opptjeningsvurdering",
                    "opptjeningsvurderingId" to vurdering.id,
                )
                return@medMdc HarVurdering(fødselsnummer, skjæringstidspunkt, vurdering.id)
            }

            opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt)?.takeUnless { it.erAvsluttet }?.let {
                loggInfo(
                    "Opptjeningsprøving pågår allerede. Etterspør grunnlaget på nytt",
                    "opptjeningsprøvingId" to it.id,
                )
                return@medMdc TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
            }

            val (prøving, vurdering) = Opptjeningsprøving.start(fødselsnummer, skjæringstidspunkt, arbeidssituasjon)
            opptjeningsprøvingRepository.lagre(prøving)

            if (vurdering == null) {
                loggInfo(
                    "Startet opptjeningsprøving. Venter på utestående behov",
                    "opptjeningsprøvingId" to prøving.id,
                    "uteståendeBehov" to prøving.uteståendeBehov,
                )
                return@medMdc TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
            }

            kravvurderingRepository.lagre(vurdering)
            loggInfo(
                "Opptjeningsprøving fullført uten innhenting",
                "opptjeningsprøvingId" to prøving.id,
                "opptjeningsvurderingId" to vurdering.id,
            )
            HarVurdering(fødselsnummer, skjæringstidspunkt, vurdering.id)
        }

    fun behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
        arbeidsforhold: List<Arbeidsforhold>,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): BehandleGrunnlagResultat =
        medMdc(
            OpptjeningMdcKeys.FØDSELSNUMMER to fødselsnummer,
            OpptjeningMdcKeys.SKJÆRINGSTIDSPUNKT to skjæringstidspunkt.toString(),
        ) {
            val prøving = opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt)

            if (prøving == null) {
                loggError("Mottatt grunnlag for opptjening, men fant ingen prøving")
                return@medMdc BehandleGrunnlagResultat.IngenPrøvingFunnet
            }

            if (prøving.erAvsluttet) {
                loggInfo(
                    "Mottatt grunnlag for opptjening, men prøving er allerede avsluttet",
                    "opptjeningsprøvingId" to prøving.id,
                )
                return@medMdc BehandleGrunnlagResultat.AlleredeVurdert
            }

            val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold))
            kravvurderingRepository.lagre(vurdering)
            opptjeningsprøvingRepository.lagre(prøving)
            loggInfo(
                "Opptjeningsprøving fullført",
                "opptjeningsprøvingId" to prøving.id,
                "opptjeningsvurderingId" to vurdering.id,
            )
            BehandleGrunnlagResultat.NyVurderingForetatt(fødselsnummer, skjæringstidspunkt, vurdering.id)
        }

    sealed class BehandleGrunnlagResultat {
        data class NyVurderingForetatt(
            val fødselsnummer: String,
            val skjæringstidspunkt: LocalDate,
            val opptjeningsvurderingId: OpptjeningsvurderingId,
        ) : BehandleGrunnlagResultat()

        data object AlleredeVurdert : BehandleGrunnlagResultat()

        data object IngenPrøvingFunnet : BehandleGrunnlagResultat()
    }

    fun finnOpptjeningsvurdering(opptjeningsvurderingId: OpptjeningsvurderingId): Opptjeningsvurdering =
        kravvurderingRepository.finn(opptjeningsvurderingId)
            ?: error("Fant ikke opptjeningsvurdering med id $opptjeningsvurderingId")
}
