package no.nav.helse.sykepenger.vilkarsproving.rest

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.openapi.OpenApiConfig
import no.nav.helse.speil.backend.app.openapi.configureOpenApiPlugin
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureResources
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.get
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VilkårsvurderingerForPersonOpenApiTest {
    private fun Application.settOppTestapp() {
        configureContentNegotiation()
        configureResources()
        val restAdapter =
            RestAdapter<AppRolle, Transaksjonskontekst>(
                personPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
                populasjonstilgangskontrollProvider =
                    object : com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider {
                        override fun kontrollerKomplettTilgang(
                            accessToken: String,
                            fødselsnummer: String,
                        ) = com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat.Ok

                        override fun kontrollerKjerneTilgang(
                            accessToken: String,
                            fødselsnummer: String,
                        ) = com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat.Ok

                        override fun kontrollerKjerneTilgangForAnsatt(
                            ansattId: String,
                            fødselsnummer: String,
                        ) = com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat.Ok
                    },
                auditlogger = Auditlogger("test"),
                transaksjonProvider = InMemoryTransaksjonProvider(),
            )
        configureOpenApiPlugin(OpenApiConfig(eksponerOpenApi = true, tittel = "sp-vilkarsproving"))
        routing {
            get(GetVilkårsvurderingerForPersonBehandler(), restAdapter)
        }
    }

    @Test
    fun `openapi-specen dokumenterer de nye query-parametrene som typet uuid`() =
        testApplication {
            application { settOppTestapp() }
            startApplication()

            val response = client.get("/api/openapi.json")

            assertEquals(HttpStatusCode.OK, response.status)
            val spec = response.bodyAsText()

            assertTrue(spec.contains("/api/personer/{personId}/vilkarsvurderinger")) { "Forventet at ruten var dokumentert: $spec" }
            assertTrue(spec.contains("opptjeningsvurderingId")) { "Forventet query-parameteret opptjeningsvurderingId i spec-en: $spec" }
            assertTrue(spec.contains("medlemskapsvurderingId")) { "Forventet query-parameteret medlemskapsvurderingId i spec-en: $spec" }
            assertTrue(spec.contains("\"format\" : \"uuid\"")) { "Forventet at UUID-parametrene var typet med format uuid: $spec" }
        }
}
