package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.AZURE_AD_AUTHENTICATION_NAME
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle

/**
 * Registrerer appens endepunkter bak Azure AD-autentisering. Alle ruter som settes opp via
 * [RestRuting] MÅ ligge inni `authenticate`-blokken — uten den blir ingen principal satt på kallet,
 * og [RestAdapter] svarer 401 «Uautentisert» selv for kall med gyldig token.
 */
fun <ROLLE : Brukerrolle, TRANSAKSJON> Application.configureRestRuting(
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit,
) {
    routing {
        authenticate(AZURE_AD_AUTHENTICATION_NAME) {
            RestRuting(this, restAdapter).endepunkter()
        }
    }
}

class RestRuting<ROLLE : Brukerrolle, TRANSAKSJON> internal constructor(
    @PublishedApi internal val route: Route,
    @PublishedApi internal val restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode> get(
        behandler: GetBehandler<RESOURCE, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { get(behandler, restAdapter) }

    inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode> post(
        behandler: PostBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    ) = with(route) { post(behandler, restAdapter) }
}
