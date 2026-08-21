package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.rest.GetBehandler
import no.nav.helse.speil.backend.app.rest.KallKontekst
import no.nav.helse.speil.backend.app.rest.RestResponse
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId

internal class GetVilkårsvurderingerForPersonBehandler : GetBehandler<ApiVilkårsvurderingerForPersonResource, ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil, AppRolle, Transaksjonskontekst> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiVilkårsvurderingerForPersonResource,
        kallKontekst: KallKontekst<Transaksjonskontekst, AppRolle>,
    ): RestResponse<ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiVilkårsvurderingerForPersonFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiVilkårsvurderingerForPersonFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val opptjeningsvurderingId = resource.opptjeningsvurderingId
            val vurdering = kallKontekst.transaksjon.vilkårsvurderinger.finn(Vilkår.Opptjening, VurderingId(opptjeningsvurderingId))
            if (vurdering == null || vurdering.fødselsnummer != identitetsnummer.value) {
                return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.VurderingIkkeFunnet)
            }
            RestResponse.ok(ApiVilkårsvurderingerForPersonResponse(opptjeningsvurdering = vurdering.tilApiOpptjeningsvurderingResponse()))
        }
    }
}
