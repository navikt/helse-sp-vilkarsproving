package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.SaksbehandlerPrincipal
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.PersonPseudoIdProvider
import no.nav.sykepenger.libs.logging.loggDebug

class RestAdapter<ROLLE : Brukerrolle, TRANSAKSJON>(
    private val personPseudoIdProvider: PersonPseudoIdProvider,
    private val populasjonstilgangskontrollProvider: PopulasjonstilgangskontrollProvider,
    private val transaksjonProvider: TransaksjonProvider<TRANSAKSJON>,
) {
    suspend fun <RESOURCE : Any, RESPONSE, ERROR : ApiErrorCode> håndter(
        call: ApplicationCall,
        resource: RESOURCE,
        behandler: RestBehandler<ROLLE>,
        kjørBehandler: (resource: RESOURCE, kallKontekst: KallKontekst<TRANSAKSJON, ROLLE>) -> RestResponse<RESPONSE, ERROR>,
    ) {
        val principal =
            call.principal<SaksbehandlerPrincipal<ROLLE>>()
                ?: return call.respondProblem(RammeverkFeilkode.Uautentisert)

        if (behandler.påkrevdTilgang !in principal.tilganger) {
            loggDebug(
                "403: saksbehandler mangler påkrevd tilgang",
                "navIdent" to principal.saksbehandler.navIdent.value,
                "påkrevdTilgang" to behandler.påkrevdTilgang.toString(),
                "harTilganger" to principal.tilganger.toString(),
            )
            return call.respondProblem(RammeverkFeilkode.ManglerTilgang)
        }
        if (!principal.brukerroller.containsAll(behandler.påkrevdeBrukerroller)) {
            loggDebug(
                "403: saksbehandler mangler påkrevd brukerrolle",
                "navIdent" to principal.saksbehandler.navIdent.value,
                "påkrevdeBrukerroller" to behandler.påkrevdeBrukerroller.toString(),
                "harBrukerroller" to principal.brukerroller.toString(),
            )
            return call.respondProblem(RammeverkFeilkode.ManglerTilgang)
        }

        val restResponse =
            transaksjonProvider.transaksjon { transaksjon ->
                val kallKontekst =
                    KallKontekst(
                        saksbehandler = principal.saksbehandler,
                        tilganger = principal.tilganger,
                        brukerroller = principal.brukerroller,
                        transaksjon = transaksjon,
                        accessToken = principal.accessToken,
                        personPseudoIdProvider = personPseudoIdProvider,
                        populasjonstilgangskontrollProvider = populasjonstilgangskontrollProvider,
                    )
                kjørBehandler(resource, kallKontekst)
            }

        when (restResponse) {
            is RestResponse.Ok -> call.respond(HttpStatusCode.OK, restResponse.body as Any)
            is RestResponse.Feil -> call.respondProblem(restResponse.feil, restResponse.detalj)
        }
    }

    private suspend fun ApplicationCall.respondProblem(
        feil: ApiErrorCode,
        detalj: String? = null,
    ) {
        respond(
            HttpStatusCode.fromValue(feil.httpStatus),
            ProblemDetails(
                title = feil.tittel,
                status = feil.httpStatus,
                detail = detalj,
                instance = request.uri,
            ),
        )
    }
}
