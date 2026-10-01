package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClientException
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.logging.loggWarn
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse

internal class GetVilkårsvurderingerForPersonBehandler(
    private val spleisService: SpleisOpptjeningsvurderingService,
) : GetBehandler<ApiVilkårsvurderingerForPersonResource, ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil, AppRolle, Transaksjonskontekst> {
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

            val vurdering =
                kallKontekst.transaksjon.opptjeningsvurderinger
                    .finn(OpptjeningsvurderingId(resource.opptjeningsvurderingId))
                    ?: try {
                        spleisService.finn(OpptjeningsvurderingId(resource.opptjeningsvurderingId), identitetsnummer.value)
                    } catch (ex: SpleisClientException) {
                        // warn log
                        loggWarn("SpleisClientException ved henting av opptjeningsvurdering: ${ex.message}")
                        return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.SpleisUtilgjengelig)
                    }

            if (vurdering == null || vurdering.fødselsnummer != identitetsnummer.value) {
                return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.VurderingIkkeFunnet)
            }

            RestResponse.ok(Vurderingsrespons.fra(vurdering))
        }
    }
}
