package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.HandlingType
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.ManuellOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.ManuellOpptjeningsvurderingResultat
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.SvarPåOpptjening
import no.nav.helse.sykepenger.vilkarsproving.domain.UgyldigSvarPåOpptjening
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.PostBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import java.time.Instant

/**
 * Samlet manuell vurdering av opptjening etter § 8-2 første og andre ledd.
 *
 * Ikke oppfylt avvises inntil videre.
 */
internal class PostManuellOpptjeningsvurderingBehandler : PostBehandler<
        ApiManuellOpptjeningsvurderingResource,
        ApiManuellOpptjeningsvurderingRequest,
        ApiManuellOpptjeningsvurderingResponse,
        ApiManuellOpptjeningsvurderingFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiManuellOpptjeningsvurderingResource,
        request: ApiManuellOpptjeningsvurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiManuellOpptjeningsvurderingResponse, ApiManuellOpptjeningsvurderingFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiManuellOpptjeningsvurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiManuellOpptjeningsvurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiManuellOpptjeningsvurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val svar = request.tilSvar()
            if (!svar.minst4Uker || svar.yrkesaktivMinst4UkerFørUttak == false) {
                return@medPerson RestResponse.feil(ApiManuellOpptjeningsvurderingFeil.IkkeOppfyltStøttesIkke)
            }

            val saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value
            val vilkårsvurderinger =
                when (
                    val resultat =
                        ManuellOpptjeningsvurdering.vurder(
                            svar = svar,
                            saksbehandlerIdent = saksbehandlerIdent,
                            fritekstbegrunnelse = request.fritekstbegrunnelse,
                            journalpostId = request.journalpostId,
                        )
                ) {
                    is ManuellOpptjeningsvurderingResultat.Ugyldig -> return@medPerson RestResponse.feil(resultat.ugyldighet.tilApi())
                    is ManuellOpptjeningsvurderingResultat.Vurdert -> resultat.vilkårsvurderinger
                }

            val opptjeningsvurdering =
                Opptjeningsvurdering.avSaksbehandler(
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    vilkårsvurderinger = vilkårsvurderinger,
                    forrigeVurdering =
                        kallKontekst.transaksjon.opptjeningsvurderinger.gjeldende(
                            fødselsnummer = identitetsnummer.value,
                            skjæringstidspunkt = request.skjæringstidspunkt,
                        ),
                )

            kallKontekst.transaksjon.opptjeningsvurderinger.lagre(opptjeningsvurdering)

            kallKontekst.transaksjon.outbox.leggTil(
                OutboxKonvolutt.ny(
                    melding =
                        OutboxMelding.Saksbehandlerhandling(
                            skjæringstidspunkt = request.skjæringstidspunkt,
                            saksbehandlerIdent = saksbehandlerIdent,
                            handlingId = opptjeningsvurdering.id.value,
                            handlingType = HandlingType.MANUELT_VURDERT_OPPTJENING,
                            kreverTotrinnsvurdering = true,
                            tidspunkt = Instant.now(),
                        ),
                    identitetsnummer = identitetsnummer,
                ),
            )
            kallKontekst.transaksjon.outbox.leggTil(
                OutboxKonvolutt.ny(
                    melding =
                        OutboxMelding.OpptjeningsvurderingEndret(
                            skjæringstidspunkt = request.skjæringstidspunkt,
                            opptjeningsvurderingId = opptjeningsvurdering.id.value,
                            manuellVurdering = true,
                        ),
                    identitetsnummer = identitetsnummer,
                ),
            )

            RestResponse.ok(ApiManuellOpptjeningsvurderingResponse(opptjeningsvurderingId = opptjeningsvurdering.id.value))
        }
    }
}

private fun ApiManuellOpptjeningsvurderingRequest.tilSvar() =
    SvarPåOpptjening(
        minst4Uker = minst4Uker,
        opptjeningsaktiviteter = opptjeningsaktiviteter.map { it.fraApi() }.toSet(),
        foreldrepengerOpptjentPåAap = foreldrepengerOpptjentPåAap,
        yrkesaktivMinst4UkerFørUttak = yrkesaktivMinst4UkerFørUttak,
    )

private fun ApiOpptjeningsaktivitet.fraApi(): Opptjeningsaktivitet =
    when (this) {
        ApiOpptjeningsaktivitet.ARBEID -> Opptjeningsaktivitet.ARBEID
        ApiOpptjeningsaktivitet.DAGPENGER -> Opptjeningsaktivitet.DAGPENGER
        ApiOpptjeningsaktivitet.SYKEPENGER -> Opptjeningsaktivitet.SYKEPENGER
        ApiOpptjeningsaktivitet.KAPITTEL_9 -> Opptjeningsaktivitet.KAPITTEL_9
        ApiOpptjeningsaktivitet.FORELDREPENGER -> Opptjeningsaktivitet.FORELDREPENGER
        ApiOpptjeningsaktivitet.SVANGERSKAPSPENGER -> Opptjeningsaktivitet.SVANGERSKAPSPENGER
    }

private fun UgyldigSvarPåOpptjening.tilApi(): ApiManuellOpptjeningsvurderingFeil =
    when (this) {
        UgyldigSvarPåOpptjening.OPPTJENINGSAKTIVITETER_MANGLER -> ApiManuellOpptjeningsvurderingFeil.OpptjeningsaktiviteterMangler
        UgyldigSvarPåOpptjening.OPPTJENINGSAKTIVITETER_UTEN_MINST_4_UKER ->
            ApiManuellOpptjeningsvurderingFeil.OpptjeningsaktiviteterUtenMinst4Uker
        UgyldigSvarPåOpptjening.FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER ->
            ApiManuellOpptjeningsvurderingFeil.ForeldrepengerOpptjentPåAapMangler
        UgyldigSvarPåOpptjening.FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER ->
            ApiManuellOpptjeningsvurderingFeil.ForeldrepengerOpptjentPåAapUtenForeldrepenger
        UgyldigSvarPåOpptjening.YRKESAKTIV_FØR_UTTAK_MANGLER -> ApiManuellOpptjeningsvurderingFeil.YrkesaktivFørUttakMangler
        UgyldigSvarPåOpptjening.YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP ->
            ApiManuellOpptjeningsvurderingFeil.YrkesaktivFørUttakUtenForeldrepengerOpptjentPåAap
    }
