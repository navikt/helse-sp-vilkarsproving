package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
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
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class VilkårsvurderingerForPersonOpenApiTest {
    private fun Application.settOppTestapp() {
        configureContentNegotiation()
        configureResources()
        val restAdapter =
            RestAdapter<AppRolle, Transaksjonskontekst>(
                personPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
                populasjonstilgangskontrollProvider =
                    object : PopulasjonstilgangskontrollProvider {
                        override fun kontrollerKomplettTilgang(
                            accessToken: String,
                            fødselsnummer: String,
                        ) = TilgangskontrollResultat.Ok

                        override fun kontrollerKjerneTilgang(
                            accessToken: String,
                            fødselsnummer: String,
                        ) = TilgangskontrollResultat.Ok

                        override fun kontrollerKjerneTilgangForAnsatt(
                            ansattId: String,
                            fødselsnummer: String,
                        ) = TilgangskontrollResultat.Ok
                    },
                auditlogger = Auditlogger("test"),
                transaksjonProvider = InMemoryTransaksjonProvider(),
            )
        configureOpenApiPlugin(OpenApiConfig(eksponerOpenApi = true, tittel = "sp-vilkarsproving"))
        val spleisService =
            SpleisOpptjeningsvurderingService(
                object : ISpleisClient {
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> = emptyList()
                },
            )
        routing {
            get(GetVilkårsvurderingerForPersonBehandler(spleisService), restAdapter)
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
            assertTrue(spec.contains("\"format\" : \"uuid\"")) { "Forventet at UUID-parametrene var typet med format uuid: $spec" }
        }

    /**
     * Kilde- og grunnlagsvariantene er unioner, og en union uten diskriminator er ubrukelig for en
     * konsument som genererer typer fra spec-en: variantene ville ikke vært til å skille fra
     * hverandre.
     *
     * Diskriminatoren settes av Jackson, mens spec-en genereres fra kotlinx-annotasjonene. De to kan
     * altså komme i utakt uten at noe annet ryker, og denne testen er det eneste som fanger det.
     */
    @Test
    fun `openapi-specen dokumenterer diskriminatoren paa alle unionsvarianter`() =
        testApplication {
            application { settOppTestapp() }
            startApplication()

            val schemas =
                jacksonObjectMapper()
                    .readTree(client.get("/api/openapi.json").bodyAsText())["components"]["schemas"]

            listOf(
                "ApiOpptjeningsvurdering.VurdertISpeil" to "kravkilde",
                "ApiOpptjeningsvurdering.OverførtFraInfotrygd" to "kravkilde",
                "ApiVurderingskilde.Automatisk" to "kildetype",
                "ApiVurderingskilde.Saksbehandler" to "kildetype",
                "ApiVurderingsgrunnlag.Arbeidsforhold" to "grunnlagstype",
                "ApiVurderingsgrunnlag.SelvstendigNæringsdrivende" to "grunnlagstype",
            ).forEach { (skjema, diskriminator) ->
                assertTrue(schemas[skjema]?.get("properties")?.has(diskriminator) == true) {
                    "Forventet diskriminatoren $diskriminator i skjemaet $skjema: ${schemas[skjema]}"
                }
            }
        }
}
