package no.nav.helse.sykepenger.vilkarsproving.application

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.HarVurdering
import no.nav.helse.sykepenger.vilkarsproving.application.VurderOpptjeningResultat.TrengerArbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Kategori
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.sykepenger.libs.logging.MdcKey
import no.nav.sykepenger.libs.logging.loggError
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.medMdc
import java.time.LocalDate

internal class OpptjeningService(
    kontekst: Transaksjonskontekst,
    private val versjonAvKode: String = "test",
) {
    private val opptjeningsvurderingRepository = kontekst.opptjeningsvurderinger
    private val opptjeningsprøvingRepository = kontekst.opptjeningsprøvinger
    private val outbox = kontekst.outbox

    fun vurderOpptjening(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        arbeidssituasjon: Arbeidssituasjon,
    ): VurderOpptjeningResultat =
        medMdc(
            MdcKey.IDENTITETSNUMMER to fødselsnummer,
        ) {
            opptjeningsvurderingRepository
                .gjeldende(fødselsnummer, skjæringstidspunkt)
                ?.takeIf { it.kategori == arbeidssituasjon.kategori }
                ?.let { vurdering ->
                    loggInfo(
                        "Har allerede opptjeningsvurdering",
                        "opptjeningsvurderingId" to vurdering.id.toString(),
                        "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                    )
                    return@medMdc HarVurdering(fødselsnummer, skjæringstidspunkt, vurdering.id)
                }

            opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt, arbeidssituasjon.kategori)?.takeUnless { it.erAvsluttet }?.let {
                loggInfo(
                    "Opptjeningsprøving pågår allerede. Etterspør grunnlaget på nytt",
                    "opptjeningsprøvingId" to it.id.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
            }

            val (prøving, vurdering) =
                Opptjeningsprøving.start(
                    fødselsnummer,
                    skjæringstidspunkt,
                    arbeidssituasjon,
                    versjonAvKode,
                )
            opptjeningsprøvingRepository.lagre(prøving)

            if (vurdering == null) {
                loggInfo(
                    "Startet opptjeningsprøving. Venter på utestående behov",
                    "opptjeningsprøvingId" to prøving.id.toString(),
                    "uteståendeBehov" to prøving.uteståendeBehov.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc TrengerArbeidsforhold(fødselsnummer, skjæringstidspunkt)
            }

            opptjeningsvurderingRepository.lagre(vurdering)
            loggInfo(
                "Opptjeningsprøving fullført uten innhenting",
                "opptjeningsprøvingId" to prøving.id.toString(),
                "opptjeningsvurderingId" to vurdering.id.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
            )
            HarVurdering(fødselsnummer, skjæringstidspunkt, vurdering.id)
        }

    private val Arbeidssituasjon.kategori: Kategori
        get() =
            when (this) {
                Arbeidssituasjon.Arbeidstaker -> Kategori.Arbeidstaker
                Arbeidssituasjon.SelvstendigNæringsdrivende -> Kategori.SelvstendigNæringsdrivende
            }

    fun behandleGrunnlagForAutomatiskArbeidstakerOpptjeningsvurdering(
        arbeidsforhold: List<Arbeidsforhold>,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): BehandleGrunnlagResultat =
        medMdc(
            MdcKey.IDENTITETSNUMMER to fødselsnummer,
        ) {
            val prøving = opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt, Kategori.Arbeidstaker)

            if (prøving == null) {
                loggError("Mottatt grunnlag for opptjening, men fant ingen prøving", "skjæringstidspunkt" to skjæringstidspunkt.toString())
                return@medMdc BehandleGrunnlagResultat.IngenPrøvingFunnet
            }

            if (prøving.erAvsluttet) {
                loggInfo(
                    "Mottatt grunnlag for opptjening, men prøving er allerede avsluttet",
                    "opptjeningsprøvingId" to prøving.id.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc BehandleGrunnlagResultat.AlleredeVurdert
            }

            val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold), versjonAvKode)
            opptjeningsvurderingRepository.lagre(vurdering)
            opptjeningsprøvingRepository.lagre(prøving)
            loggInfo(
                "Opptjeningsprøving fullført",
                "opptjeningsprøvingId" to prøving.id.toString(),
                "opptjeningsvurderingId" to vurdering.id.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
            )
            BehandleGrunnlagResultat.NyVurderingForetatt(fødselsnummer, skjæringstidspunkt, vurdering.id)
        }

    /**
     * Starter en ny automatisk opptjeningsprøving for et skjæringstidspunkt som allerede er vurdert,
     * og varsler Spleis via outboxen når den nye vurderingen er klar. Ved [VurderOpptjeningPåNyttResultat.VenterPåGrunnlag]
     * er det kallerens ansvar å be om arbeidsforhold for prøvingen.
     */
    fun vurderOpptjeningPåNytt(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): VurderOpptjeningPåNyttResultat =
        medMdc(
            MdcKey.IDENTITETSNUMMER to fødselsnummer,
        ) {
            val gjeldende =
                opptjeningsvurderingRepository.gjeldende(fødselsnummer, skjæringstidspunkt)
                    ?: return@medMdc VurderOpptjeningPåNyttResultat.IngenVurderingFunnet

            opptjeningsprøvingRepository.finnSiste(fødselsnummer, skjæringstidspunkt, gjeldende.kategori)?.takeUnless { it.erAvsluttet }?.let {
                loggInfo(
                    "Kan ikke vurdere opptjening på nytt, opptjeningsprøving pågår allerede",
                    "opptjeningsprøvingId" to it.id.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc VurderOpptjeningPåNyttResultat.PrøvingPågår
            }

            val (prøving, vurdering) =
                Opptjeningsprøving.start(
                    fødselsnummer,
                    skjæringstidspunkt,
                    gjeldende.kategori.arbeidssituasjon,
                    versjonAvKode,
                )
            opptjeningsprøvingRepository.lagre(prøving)

            if (vurdering == null) {
                loggInfo(
                    "Startet ny opptjeningsprøving. Venter på arbeidsforhold",
                    "opptjeningsprøvingId" to prøving.id.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc VurderOpptjeningPåNyttResultat.VenterPåGrunnlag(prøving.id)
            }

            lagreOgVarsleEndring(vurdering)
            loggInfo(
                "Opptjening vurdert på nytt uten innhenting",
                "opptjeningsprøvingId" to prøving.id.toString(),
                "opptjeningsvurderingId" to vurdering.id.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
            )
            VurderOpptjeningPåNyttResultat.NyVurderingForetatt(vurdering.id)
        }

    sealed class VurderOpptjeningPåNyttResultat {
        data class NyVurderingForetatt(
            val opptjeningsvurderingId: OpptjeningsvurderingId,
        ) : VurderOpptjeningPåNyttResultat()

        data class VenterPåGrunnlag(
            val opptjeningsprøvingId: OpptjeningsprøvingId,
        ) : VurderOpptjeningPåNyttResultat()

        data object IngenVurderingFunnet : VurderOpptjeningPåNyttResultat()

        data object PrøvingPågår : VurderOpptjeningPåNyttResultat()
    }

    fun behandleGrunnlagForNyArbeidstakerOpptjeningsvurdering(
        arbeidsforhold: List<Arbeidsforhold>,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        opptjeningsprøvingId: OpptjeningsprøvingId,
    ): BehandleGrunnlagResultat =
        medMdc(
            MdcKey.IDENTITETSNUMMER to fødselsnummer,
        ) {
            val prøving =
                opptjeningsprøvingRepository
                    .finnSiste(fødselsnummer, skjæringstidspunkt, Kategori.Arbeidstaker)
                    ?.takeIf { it.id == opptjeningsprøvingId }

            if (prøving == null) {
                loggError(
                    "Mottatt grunnlag for ny opptjeningsvurdering, men fant ikke prøvingen som siste prøving",
                    "opptjeningsprøvingId" to opptjeningsprøvingId.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc BehandleGrunnlagResultat.IngenPrøvingFunnet
            }

            if (prøving.erAvsluttet) {
                loggInfo(
                    "Mottatt grunnlag for ny opptjeningsvurdering, men prøving er allerede avsluttet",
                    "opptjeningsprøvingId" to prøving.id.toString(),
                    "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                )
                return@medMdc BehandleGrunnlagResultat.AlleredeVurdert
            }

            val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold), versjonAvKode)
            opptjeningsprøvingRepository.lagre(prøving)
            lagreOgVarsleEndring(vurdering)
            loggInfo(
                "Ny opptjeningsprøving fullført",
                "opptjeningsprøvingId" to prøving.id.toString(),
                "opptjeningsvurderingId" to vurdering.id.toString(),
                "skjæringstidspunkt" to skjæringstidspunkt.toString(),
            )
            BehandleGrunnlagResultat.NyVurderingForetatt(fødselsnummer, skjæringstidspunkt, vurdering.id)
        }

    private fun lagreOgVarsleEndring(vurdering: Opptjeningsvurdering) {
        opptjeningsvurderingRepository.lagre(vurdering)
        outbox.leggTil(
            OutboxKonvolutt.ny(
                melding =
                    OutboxMelding.OpptjeningsvurderingEndret(
                        skjæringstidspunkt = vurdering.skjæringstidspunkt,
                        opptjeningsvurderingId = vurdering.id.value,
                        manuellVurdering = false,
                    ),
                identitetsnummer = Identitetsnummer(vurdering.fødselsnummer),
            ),
        )
    }

    private val Kategori.arbeidssituasjon: Arbeidssituasjon
        get() =
            when (this) {
                Kategori.Arbeidstaker -> Arbeidssituasjon.Arbeidstaker
                Kategori.SelvstendigNæringsdrivende -> Arbeidssituasjon.SelvstendigNæringsdrivende
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
        opptjeningsvurderingRepository.finn(opptjeningsvurderingId)
            ?: error("Fant ikke opptjeningsvurdering med id $opptjeningsvurderingId")
}
