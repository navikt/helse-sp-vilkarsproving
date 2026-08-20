package no.nav.helse.sykepenger.vilkarsproving.infra.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.helse.sykepenger.vilkarsproving.application.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.shared.logging.loggInfo
import no.nav.helse.sykepenger.vilkarsproving.shared.logging.teamLogs
import org.slf4j.event.Level
import java.net.URI
import java.util.*

data class VilkårsvurderingRequest(
    val identitetsnummer: String,
)

data class VilkårsvurderingResponse(
    val hello: Boolean,
)

data class ProblemResponse(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
)

internal fun Application.vilkårsprøvingApi(
    transaksjonProvider: TransaksjonProvider,
    clientId: String,
    issuerUrl: String,
    jwkProviderUri: String,
) {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
    }
    install(CallLogging) {
        disableDefaultColors()
        logger = teamLogs
        level = Level.INFO
        callIdMdc("callId")
        filter { call -> call.request.path() !in setOf("/metrics", "/isalive", "/isready") }
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ProblemResponse(
                    title = "Ugyldig forespørsel",
                    status = HttpStatusCode.BadRequest.value,
                    detail = cause.message ?: "Validering feilet",
                    instance = call.request.uri,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            teamLogs.error("Uventet feil ved kall til ${call.request.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ProblemResponse(
                    title = "Intern serverfeil",
                    status = HttpStatusCode.InternalServerError.value,
                    detail = "En uventet feil oppstod",
                    instance = call.request.uri,
                ),
            )
        }
    }
    authentication {
        jwt("oidc") {
            verifier(
                jwkProvider = JwkProviderBuilder(URI(jwkProviderUri).toURL()).build(),
                issuer = issuerUrl,
            ) {
                withAudience(clientId)
            }
            validate { credentials -> JWTPrincipal(credentials.payload) }
        }
    }
    routing {
        authenticate("oidc") {
            post("/vilkarsvurderinger/{vilkårsvurderingId}") {
                val request = call.receive<VilkårsvurderingRequest>()
                require(request.identitetsnummer.matches(Regex("\\d{11}"))) {
                    "identitetsnummer må bestå av nøyaktig 11 siffer"
                }
                val rawId = call.parameters["vilkårsvurderingId"]
                val id =
                    rawId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ProblemResponse(
                                title = "Ugyldig vilkårsvurderingId",
                                status = HttpStatusCode.BadRequest.value,
                                detail = "vilkårsvurderingId må være en gyldig UUID",
                                instance = call.request.uri,
                            ),
                        )
                val response = VilkårsvurderingResponse(hello = true)
                loggInfo("Svarer på POST /vilkarsvurderinger/$id", "response" to response)

                call.respond(response)
            }
        }
    }
}
