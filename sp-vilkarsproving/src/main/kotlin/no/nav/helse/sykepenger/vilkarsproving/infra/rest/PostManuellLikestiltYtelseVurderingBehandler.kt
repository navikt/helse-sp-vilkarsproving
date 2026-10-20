package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.HandlingType
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.LikestiltYtelse
import no.nav.helse.sykepenger.vilkarsproving.domain.LikestiltYtelseVurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.LikestiltYtelseVurderingResultat
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.SvarPåLikestiltYtelse
import no.nav.helse.sykepenger.vilkarsproving.domain.UgyldigSvarPåLikestiltYtelse
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.PostBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import java.time.Instant

private fun erProdGcp(): Boolean = System.getenv()["NAIS_CLUSTER_NAME"] == "prod-gcp"

internal class PostManuellLikestiltYtelseVurderingBehandler :
    PostBehandler<
        ApiManuellLikestiltYtelseVurderingResource,
        ApiManuellLikestiltYtelseVurderingRequest,
        ApiManuellOpptjeningsvurderingResponse,
        ApiManuellLikestiltYtelseVurderingFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiManuellLikestiltYtelseVurderingResource,
        request: ApiManuellLikestiltYtelseVurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiManuellOpptjeningsvurderingResponse, ApiManuellLikestiltYtelseVurderingFeil> {
        if (erProdGcp()) {
            throw IllegalStateException("Manuell vurdering av likestilte ytelser er ikke påskrudd i prod-gcp")
        }
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiManuellLikestiltYtelseVurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiManuellLikestiltYtelseVurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiManuellLikestiltYtelseVurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value
            val vilkårsvurderinger =
                when (
                    val resultat =
                        LikestiltYtelseVurdering.vurder(
                            svar = request.tilSvar(),
                            saksbehandlerIdent = saksbehandlerIdent,
                            fritekstbegrunnelse = request.fritekstbegrunnelse,
                            journalpostId = request.journalpostId,
                        )
                ) {
                    is LikestiltYtelseVurderingResultat.Ugyldig -> return@medPerson RestResponse.feil(resultat.ugyldighet.tilApi())
                    is LikestiltYtelseVurderingResultat.Vurdert -> resultat.vilkårsvurderinger
                }

            val opptjeningsvurdering =
                Opptjeningsvurdering.avSaksbehandlerEtterAndreAvsnitt(
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

private fun ApiManuellLikestiltYtelseVurderingRequest.tilSvar() =
    SvarPåLikestiltYtelse(
        sammenlagtMinst4Uker = sammenlagtMinst4Uker,
        ytelser = ytelser.map { it.fraApi() },
        foreldrepengerOpptjentPåAap = foreldrepengerOpptjentPåAap,
        yrkesaktivMinst4UkerFørUttak = yrkesaktivMinst4UkerFørUttak,
    )

private fun ApiLikestiltYtelse.fraApi(): LikestiltYtelse =
    when (this) {
        ApiLikestiltYtelse.DAGPENGER -> LikestiltYtelse.DAGPENGER
        ApiLikestiltYtelse.SYKEPENGER -> LikestiltYtelse.SYKEPENGER
        ApiLikestiltYtelse.KAPITTEL_9 -> LikestiltYtelse.KAPITTEL_9
        ApiLikestiltYtelse.FORELDREPENGER -> LikestiltYtelse.FORELDREPENGER
        ApiLikestiltYtelse.SVANGERSKAPSPENGER -> LikestiltYtelse.SVANGERSKAPSPENGER
    }

private fun UgyldigSvarPåLikestiltYtelse.tilApi(): ApiManuellLikestiltYtelseVurderingFeil =
    when (this) {
        UgyldigSvarPåLikestiltYtelse.YTELSER_MANGLER -> ApiManuellLikestiltYtelseVurderingFeil.YtelserMangler
        UgyldigSvarPåLikestiltYtelse.YTELSER_UTEN_SAMMENLAGT_OPPTJENING ->
            ApiManuellLikestiltYtelseVurderingFeil.YtelserUtenSammenlagtOpptjening
        UgyldigSvarPåLikestiltYtelse.FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER ->
            ApiManuellLikestiltYtelseVurderingFeil.ForeldrepengerOpptjentPåAapMangler
        UgyldigSvarPåLikestiltYtelse.FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER ->
            ApiManuellLikestiltYtelseVurderingFeil.ForeldrepengerOpptjentPåAapUtenForeldrepenger
        UgyldigSvarPåLikestiltYtelse.YRKESAKTIV_FØR_UTTAK_MANGLER -> ApiManuellLikestiltYtelseVurderingFeil.YrkesaktivFørUttakMangler
        UgyldigSvarPåLikestiltYtelse.YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP ->
            ApiManuellLikestiltYtelseVurderingFeil.YrkesaktivFørUttakUtenForeldrepengerOpptjentPåAap
    }
