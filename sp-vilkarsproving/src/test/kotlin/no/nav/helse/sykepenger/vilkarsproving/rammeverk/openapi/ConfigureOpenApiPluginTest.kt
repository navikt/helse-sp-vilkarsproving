package no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.resources.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

@Resource("/api/test-endepunkt")
private class TestResource

class ConfigureOpenApiPluginTest {
    @Test
    fun `openapi-json svarer 404 naar eksponerOpenApi er false`() =
        testApplication {
            application { configureOpenApiPlugin(OpenApiConfig(eksponerOpenApi = false, tittel = "test")) }
            val response = client.get("/api/openapi.json")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `openapi-json svarer 200 og dokumenterer registrerte ruter naar eksponerOpenApi er true`() =
        testApplication {
            application {
                install(Resources)
                configureOpenApiPlugin(OpenApiConfig(eksponerOpenApi = true, tittel = "test"))
                routing {
                    get<TestResource> { call.respondText("ok") }
                    get("/isalive") { call.respondText("ok") }
                }
            }

            val response = client.get("/api/openapi.json")

            assertEquals(HttpStatusCode.OK, response.status)
            val spec = jacksonObjectMapper().readTree(response.bodyAsText())
            assertEquals("test", spec["info"]["title"].asString())
            val stier = spec["paths"].propertyNames().toList()
            assertEquals(listOf("/api/test-endepunkt"), stier) { "Forventet bare den registrerte API-ruten, fikk: $stier" }
        }

    @Test
    fun `swagger ui serveres naar eksponerOpenApi er true`() =
        testApplication {
            application {
                install(Resources)
                configureOpenApiPlugin(OpenApiConfig(eksponerOpenApi = true, tittel = "test"))
                routing {
                    get<TestResource> { call.respondText("ok") }
                }
            }

            assertEquals(HttpStatusCode.OK, client.get("/api/swagger").status)
            val spec = client.get("/api/swagger/openapi.json")
            assertEquals(HttpStatusCode.OK, spec.status)
            assert(spec.bodyAsText().contains("/api/test-endepunkt"))
        }

    @Test
    fun `default eksponerOpenApi er false`() {
        assertEquals(false, OpenApiConfig(tittel = "test").eksponerOpenApi)
    }

    @Test
    fun `EKSPONER_OPENAPI=true i env slaar paa eksponering`() {
        val config = OpenApiConfig.fraEnv("test-app", mapOf("EKSPONER_OPENAPI" to "true"))
        assertEquals(true, config.eksponerOpenApi)
    }
}
