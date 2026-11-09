package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForNyArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.domain.Hovedkategori
import no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori.FakeRettighetskategoriClient
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.post
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.sykepenger.libs.ktor.auth.AccessToken
import no.nav.sykepenger.libs.ktor.auth.NavIdent
import no.nav.sykepenger.libs.ktor.auth.Saksbehandler
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerOid
import no.nav.sykepenger.libs.ktor.auth.SaksbehandlerPrincipal
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

internal class PostNyAutomatiskVilkårsvurderingBehandlerTest {
    private val transaksjonProvider = InMemoryTransaksjonProvider()
    private val rettighetskategorier = FakeRettighetskategoriClient()
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingRiver(this, transaksjonProvider, rettighetskategorier)
            GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(this, transaksjonProvider)
            GrunnlagForNyArbeidstakerOpptjeningsvurderingRiver(this, transaksjonProvider)
        }
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `ny vurdering uten innhenting får rettighetskategori-ID-ene til vurderingen den erstatter`() =
        testApplication {
            val pseudoId = settOpp()
            val rettighetskategoriId = UUID.randomUUID()
            rettighetskategorier.registrer(rettighetskategoriId, Hovedkategori.SelvstendigNæringsdrivende)
            rapid.sendTestMessage(opptjeningsvurderingBehov("SelvstendigNæringsdrivende", rettighetskategoriId))
            val erstattet = løsningsIder().single()

            val response = client.vurderPåNytt(pseudoId)
            val ny = objectMapper.readTree(response.bodyAsText())["opptjeningsvurderingId"].asString()
            rapid.sendTestMessage(opptjeningsvurderingBehov("SelvstendigNæringsdrivende", rettighetskategoriId))

            assertEquals(HttpStatusCode.OK, response.status)
            assertNotEquals(erstattet, ny)
            assertEquals(listOf(erstattet, ny), løsningsIder())
        }

    @Test
    fun `ny vurdering etter innhenting av arbeidsforhold får rettighetskategori-ID-ene til vurderingen den erstatter`() =
        testApplication {
            val pseudoId = settOpp()
            val rettighetskategoriId = UUID.randomUUID()
            rapid.sendTestMessage(opptjeningsvurderingBehov("Arbeidstaker", rettighetskategoriId))
            rapid.sendTestMessage(medArbeidsforhold(sisteMelding()))
            val erstattet = løsningsIder().single()

            val response = client.vurderPåNytt(pseudoId)
            assertEquals(HttpStatusCode.OK, response.status)
            rapid.sendTestMessage(medArbeidsforhold(sisteMelding()))
            val ny =
                transaksjonProvider.outbox.meldinger
                    .map { objectMapper.readTree(it.melding) }
                    .single { it.path("handlingType").asString() == "NY_AUTOMATISK_OPPTJENINGSVURDERING" }
                    .path("handlingId")
                    .asString()
            rapid.sendTestMessage(opptjeningsvurderingBehov("Arbeidstaker", rettighetskategoriId))

            assertNotEquals(erstattet, ny)
            assertEquals(listOf(erstattet, ny), løsningsIder())
        }

    private fun løsningsIder() =
        (0 until rapid.inspektør.size)
            .map { rapid.inspektør.message(it) }
            .filter { it.path("@løsning").has("Opptjeningsvurdering") }
            .map { it.path("@løsning").path("Opptjeningsvurdering").path("id").asString() }

    private fun sisteMelding() = rapid.inspektør.message(rapid.inspektør.size - 1) as ObjectNode

    // Svarer på behovet om arbeidsforhold slik behovsakkumulatoren gjør
    private fun medArbeidsforhold(behov: ObjectNode): String {
        val løsning = behov.deepCopy()
        løsning.put("@id", UUID.randomUUID().toString())
        løsning.put("@final", true)
        løsning
            .putObject("@løsning")
            .putArray("ArbeidsforholdV2")
            .addObject()
            .put("orgnummer", "987654321")
            .put("type", "ORDINÆRT")
            .put("ansattSiden", "2018-01-01")
            .put("ansattTil", "2018-01-31")
        return løsning.toString()
    }

    private suspend fun HttpClient.vurderPåNytt(pseudoId: String): HttpResponse =
        post("/api/personer/$pseudoId/vilkarsvurderinger/automatisk") {
            contentType(ContentType.Application.Json)
            setBody("""{ "skjæringstidspunkt": "2018-02-01" }""")
        }

    private fun ApplicationTestBuilder.settOpp(): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(Identitetsnummer(FØDSELSNUMMER))
        application {
            configureContentNegotiation()
            configureResources()
            val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
            val principal = SaksbehandlerPrincipal(saksbehandler, setOf(Tilgang.Les, Tilgang.Skriv), AccessToken("token"))
            intercept(ApplicationCallPipeline.Plugins) {
                call.authentication.principal(principal)
            }
            val restAdapter =
                RestAdapter(
                    personPseudoIdProvider = pseudoIdProvider,
                    populasjonstilgangskontrollProvider = TilgangTilAlle,
                    transaksjonProvider = transaksjonProvider,
                )
            routing {
                post(PostNyAutomatiskVilkårsvurderingBehandler(publiser = { key, melding -> rapid.publish(key, melding) }), restAdapter)
            }
        }
        return pseudoId.toString()
    }

    private object TilgangTilAlle : PopulasjonstilgangskontrollProvider {
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
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"

        @Language("JSON")
        fun opptjeningsvurderingBehov(
            arbeidssituasjon: String,
            rettighetskategoriId: UUID,
        ) = """
            {
              "@event_name": "behov",
              "@id": "${UUID.randomUUID()}",
              "@behov": ["Opptjeningsvurdering"],
              "fødselsnummer": "$FØDSELSNUMMER",
              "Opptjeningsvurdering": {
                "skjæringstidspunkt": "2018-02-01",
                "arbeidssituasjon": "$arbeidssituasjon",
                "rettighetskategoriId": "$rettighetskategoriId"
              }
            }
            """
    }
}
