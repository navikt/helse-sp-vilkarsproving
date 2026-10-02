package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle
import io.ktor.server.resources.get as resourceGet
import io.ktor.server.resources.post as resourcePost

@OptIn(ExperimentalKtorApi::class)
inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.get(
    behandler: GetBehandler<RESOURCE, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    resourceGet<RESOURCE> { resource ->
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, kallKontekst) }
    }.describe { behandler.openApiUtenRequestBody<RESOURCE, RESPONSE, ERROR, ROLLE>(this) }
}

@OptIn(ExperimentalKtorApi::class)
inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> Route.post(
    behandler: PostBehandler<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE, TRANSAKSJON>,
    restAdapter: RestAdapter<ROLLE, TRANSAKSJON>,
) {
    resourcePost<RESOURCE> { resource ->
        val request = call.receive<REQUEST>()
        restAdapter.håndter(call, resource, behandler) { r, kallKontekst -> behandler.behandle(r, request, kallKontekst) }
    }.describe { behandler.openApiMedRequestBody<RESOURCE, REQUEST, RESPONSE, ERROR, ROLLE>(this) }
}
