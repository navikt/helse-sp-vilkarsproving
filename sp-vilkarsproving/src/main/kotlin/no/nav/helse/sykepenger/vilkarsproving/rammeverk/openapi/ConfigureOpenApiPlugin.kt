package no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi

import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.registerJWTSecurityScheme
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.AZURE_AD_AUTHENTICATION_NAME
import no.nav.sykepenger.libs.logging.loggInfo

@OptIn(ExperimentalKtorApi::class)
fun Application.configureOpenApiPlugin(config: OpenApiConfig) {
    if (!config.eksponerOpenApi) {
        loggInfo("OpenAPI/Swagger er ikke eksponert (EKSPONER_OPENAPI=false)")
        return
    }
    // Lokalt brukes en annen auth-provider enn JWT under samme navn, så skjemaet registreres eksplisitt
    // i stedet for å bli utledet fra providerens type.
    registerJWTSecurityScheme(AZURE_AD_AUTHENTICATION_NAME)

    val info = OpenApiInfo(title = config.tittel, version = config.versjon)
    val kilde =
        OpenApiDocSource.Routing(
            contentType = ContentType.Application.Json,
            schemaInference = openApiSkjemainferens,
            routes = { routingRoot.descendants().filter { it.erApiRute() } },
        )
    // Rutene er ikke registrert ennå når pluginen settes opp, så spec-en må bygges ved første kall.
    val spec by lazy { kilde.read(this, OpenApiDoc(info = info)) }

    routing {
        route("/api") {
            get("openapi.json") {
                call.respondText(spec.content, spec.contentType)
            }.hide()
            swaggerUI("swagger") {
                this.info = info
                source = kilde
                remotePath = "openapi.json"
            }
        }
    }
}

private fun Route.erApiRute(): Boolean =
    generateSequence(this) { it.parent }
        .toList()
        .asReversed()
        .firstNotNullOfOrNull { (it.selector as? PathSegmentConstantRouteSelector)?.value } == "api"
