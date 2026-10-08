package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.PersonAvstemmingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClientException
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.logging.loggWarn

internal class GetOpptjeningshistorikkBehandler(
    private val avstemmingService: PersonAvstemmingService,
) : GetBehandler<ApiOpptjeningshistorikkResource, ApiOpptjeningshistorikkResponse, ApiOpptjeningshistorikkFeil, Transaksjonskontekst> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiOpptjeningshistorikkResource,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiOpptjeningshistorikkResponse, ApiOpptjeningshistorikkFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiOpptjeningshistorikkFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiOpptjeningshistorikkFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiOpptjeningshistorikkFeil.ManglerTilgang },
        ) { identitetsnummer ->
            // Avstemming kaller bare spleis for personer som ikke er migrert ennå. Feiler den, mangler
            // vi altså spleis' del av historikken, og et ufullstendig svar ville sett ut som en fullstendig historikk.
            try {
                avstemmingService.lagreOpptjeningsvurderinger(fødselsnummer = identitetsnummer.value)
            } catch (ex: SpleisClientException) {
                loggWarn("Feil ved avstemming av opptjeningsvurderinger mot Spleis", ex)
                return@medPerson RestResponse.feil(ApiOpptjeningshistorikkFeil.SpleisUtilgjengelig)
            }

            val historikk =
                kallKontekst.transaksjon.opptjeningsvurderinger
                    .historikk(identitetsnummer.value, resource.skjæringstidspunkt)

            RestResponse.ok(Vurderingsrespons.historikk(resource.skjæringstidspunkt, historikk))
        }
    }
}
