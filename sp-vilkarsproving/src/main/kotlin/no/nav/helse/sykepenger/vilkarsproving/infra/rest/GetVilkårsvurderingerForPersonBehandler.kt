package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.PersonAvstemmingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClientException
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.logging.loggWarn

internal class GetVilkårsvurderingerForPersonBehandler(
    private val avstemmingService: PersonAvstemmingService,
) : GetBehandler<ApiVilkårsvurderingerForPersonResource, ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil, Transaksjonskontekst> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiVilkårsvurderingerForPersonResource,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiVilkårsvurderingerForPersonFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiVilkårsvurderingerForPersonFeil.ManglerTilgang },
        ) { identitetsnummer ->

            val spleisFeilet =
                try {
                    avstemmingService.lagreOpptjeningsvurderinger(fødselsnummer = identitetsnummer.value)
                    false
                } catch (ex: SpleisClientException) {
                    loggWarn("Feil ved avstemming av opptjeningsvurderinger mot Spleis", ex)
                    true
                }

            val vurdering =
                kallKontekst.transaksjon.opptjeningsvurderinger
                    .finn(OpptjeningsvurderingId(resource.opptjeningsvurderingId))

            if (vurdering == null && spleisFeilet) {
                return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.SpleisUtilgjengelig)
            }
            if (vurdering == null || vurdering.fødselsnummer != identitetsnummer.value) {
                return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.VurderingIkkeFunnet)
            }

            RestResponse.ok(Vurderingsrespons.fra(vurdering))
        }
    }
}
