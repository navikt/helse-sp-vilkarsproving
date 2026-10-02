package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auditlogg.Auditlogger
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi.OpenApiConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi.configureOpenApiPlugin
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.get
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

            val response = client.get("/api/openapi.json")

            assertEquals(HttpStatusCode.OK, response.status)
            val spec = jacksonObjectMapper().readTree(response.bodyAsText())
            val operasjon = spec["paths"]["/api/personer/{personId}/vilkarsvurderinger"]?.get("get")
            assertNotNull(operasjon) { "Forventet at ruten var dokumentert: $spec" }
            val parametere = operasjon!!["parameters"].associateBy { it["name"].asString() }

            val opptjeningsvurderingId = parametere.getValue("opptjeningsvurderingId")
            assertEquals("query", opptjeningsvurderingId["in"].asString())
            assertEquals("uuid", opptjeningsvurderingId["schema"]["format"].asString())
            assertEquals("path", parametere.getValue("personId")["in"].asString())
        }

    /**
     * Kilde- og grunnlagsvariantene er unioner, og en union uten diskriminator er ubrukelig for en
     * konsument som genererer typer fra spec-en: variantene ville ikke vært til å skille fra
     * hverandre.
     *
     * Spec-en leser diskriminatoren fra Jackson-annotasjonene (`@JsonTypeInfo`/`@JsonSubTypes`), så
     * denne testen fanger det om den lesingen slutter å virke.
     */
    @Test
    fun `openapi-specen dokumenterer diskriminatoren paa alle unionsvarianter`() =
        testApplication {
            application { settOppTestapp() }

            val schemas =
                jacksonObjectMapper()
                    .readTree(client.get("/api/openapi.json").bodyAsText())["components"]["schemas"]

            listOf(
                Triple("ApiOpptjeningsvurdering.VurdertISpVilkarproving", "kravkilde", "VURDERT_I_SP_VILKARSPROVING"),
                Triple("ApiOpptjeningsvurdering.OverførtFraSpleis", "kravkilde", "OVERFORT_FRA_SPLEIS"),
                Triple("ApiOpptjeningsvurdering.OverførtFraInfotrygd", "kravkilde", "OVERFOERT_FRA_INFOTRYGD"),
                Triple("ApiVurderingskilde.Automatisk", "kildetype", "AUTOMATISK"),
                Triple("ApiVurderingskilde.Saksbehandler", "kildetype", "SAKSBEHANDLER"),
                Triple("ApiVurderingskilde.OverførtFraSpleis", "kildetype", "OVERFOERT_FRA_SPLEIS"),
                Triple("ApiVurderingsgrunnlag.Arbeidsforhold", "grunnlagstype", "ARBEIDSFORHOLD"),
                Triple("ApiVurderingsgrunnlag.SelvstendigNæringsdrivende", "grunnlagstype", "SELVSTENDIG_NAERINGSDRIVENDE"),
            ).forEach { (skjema, diskriminator, verdi) ->
                val enumverdier = schemas[skjema]?.get("properties")?.get(diskriminator)?.get("enum")?.toList()?.map { it.asString() }
                assertEquals(listOf(verdi), enumverdier) {
                    "Forventet diskriminatoren $diskriminator=$verdi i skjemaet $skjema: ${schemas[skjema]}"
                }
                assertTrue(schemas[skjema]["required"].any { it.asString() == diskriminator })
            }
        }

    @Test
    fun `openapi-specen beskriver uuid og nullbare felter slik Jackson skriver dem`() =
        testApplication {
            application { settOppTestapp() }

            val schemas =
                jacksonObjectMapper()
                    .readTree(client.get("/api/openapi.json").bodyAsText())["components"]["schemas"]

            val id = schemas["ApiOpptjeningsvurdering.VurdertISpVilkarproving"]["properties"]["id"]
            assertEquals("string", id["type"].asString())
            assertEquals("uuid", id["format"].asString())

            val avsnitt = schemas["ApiLovreferanse"]["properties"]["avsnitt"]
            assertEquals(listOf("integer", "null"), avsnitt["type"].toList().map { it.asString() })

            val opptjeningsperiode = schemas["ApiVurderingsgrunnlag.Arbeidsforhold"]["properties"]["opptjeningsperiode"]
            assertEquals(
                listOf("#/components/schemas/ApiPeriode", null),
                opptjeningsperiode["oneOf"].toList().map { it["\$ref"]?.asString() },
            )
            assertEquals("object", schemas["ApiPeriode"]["type"].asString())
        }
}
