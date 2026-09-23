package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.rest.KallKontekst
import no.nav.helse.speil.backend.app.rest.PostBehandler
import no.nav.helse.speil.backend.app.rest.RestResponse
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering

internal class PostManuellVilkårsvurderingBehandler :
    PostBehandler<
        ApiManuellVilkårsvurderingResource,
        ApiManuellVilkårsvurderingRequest,
        ApiManuellVilkårsvurderingResponse,
        ApiManuellVilkårsvurderingFeil,
        AppRolle,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    private fun erProdGcp(): Boolean = System.getenv()["NAIS_CLUSTER_NAME"] == "prod-gcp"

    override fun behandle(
        resource: ApiManuellVilkårsvurderingResource,
        request: ApiManuellVilkårsvurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst, AppRolle>,
    ): RestResponse<ApiManuellVilkårsvurderingResponse, ApiManuellVilkårsvurderingFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiManuellVilkårsvurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiManuellVilkårsvurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiManuellVilkårsvurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val vilkårskode = request.vilkårskode.fraApi()
            if (erProdGcp()) {
                throw IllegalStateException("Manuell vilkårsvurdering er ikke påskrudd i prod-gcp")
            }

            val vilkårsvurdering =
                Vilkårsvurdering.avSaksbehandler(
                    vilkårskode = vilkårskode,
                    utfall = request.utfall.fraApi(),
                    saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value,
                    fritekstbegrunnelse = request.fritekstbegrunnelse,
                    journalpostId = request.journalpostId,
                )

            val kravvurdering =
                Opptjeningsvurdering.avSaksbehandler(
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    vilkårsvurdering = vilkårsvurdering,
                    forrigeVurdering =
                        kallKontekst.transaksjon.opptjeningsvurderinger.gjeldende(
                            fødselsnummer = identitetsnummer.value,
                            skjæringstidspunkt = request.skjæringstidspunkt,
                        ),
                )

            kallKontekst.transaksjon.opptjeningsvurderinger.lagre(kravvurdering)

            kallKontekst.transaksjon.outbox.leggTil(
                OutboxKonvolutt.ny(
                    melding =
                        OutboxMelding.OpptjeningsvurderingEndret(
                            skjæringstidspunkt = request.skjæringstidspunkt,
                            opptjeningsvurderingId = kravvurdering.id.value,
                            manuellVurdering = true,
                        ),
                    identitetsnummer = identitetsnummer,
                ),
            )

            RestResponse.ok(ApiManuellVilkårsvurderingResponse(opptjeningsvurderingId = kravvurdering.id.value))
        }
    }
}

private fun ApiVilkårskode.fraApi(): Vilkårskode =
    when (this) {
        ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER -> Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
    }

private fun ApiUtfall.fraApi(): Utfall =
    when (this) {
        ApiUtfall.OPPFYLT -> Utfall.Oppfylt
        ApiUtfall.IKKE_OPPFYLT -> Utfall.IkkeOppfylt
    }
