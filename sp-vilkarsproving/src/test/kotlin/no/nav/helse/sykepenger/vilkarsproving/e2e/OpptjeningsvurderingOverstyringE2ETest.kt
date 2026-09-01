package no.nav.helse.sykepenger.vilkarsproving.e2e

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.AccessToken
import no.nav.helse.speil.backend.app.auth.NavIdent
import no.nav.helse.speil.backend.app.auth.Saksbehandler
import no.nav.helse.speil.backend.app.auth.SaksbehandlerOid
import no.nav.helse.speil.backend.app.auth.SaksbehandlerPrincipal
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureResources
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.get
import no.nav.helse.speil.backend.app.rest.post
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.infra.db.Database
import no.nav.helse.sykepenger.vilkarsproving.infra.db.DatabaseTest
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.OverstyrVilkårsvurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

internal class OpptjeningsvurderingOverstyringE2ETest : DatabaseTest() {
    /** Sett til `true` for å dumpe innholdet i databasetabellene mellom hvert steg, se [dump]. */
    private val dump = true

    private fun dump(steg: String) {
        if (!dump) return
        Database.dump("kravproving", "kravvurdering", "vilkarsvurdering", overskrift = steg)
    }

    private val transaksjon = Database.transaksjonProvider
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingRiver(this, transaksjon)
            GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(this, transaksjon)
            OpptjeningsvurderingResultatRiver(
                this,
                transaksjon,
                object : ISpleisClient {
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> {
                        TODO("Not yet implemented, skal ikke trenges når vurderingen finnes i databasen")
                    }
                },
            )
        }

    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer(FØDSELSNUMMER)
    private val objectMapper = jacksonObjectMapper()

    private fun Application.settOppTestapp(personPseudoIdProvider: InMemoryPersonPseudoIdProvider) {
        configureContentNegotiation()
        configureResources()

        val principal =
            SaksbehandlerPrincipal(
                saksbehandler,
                setOf(Tilgang.Les, Tilgang.Skriv),
                emptySet<AppRolle>(),
                AccessToken("token"),
            )
        intercept(ApplicationCallPipeline.Plugins) {
            call.authentication.principal(principal)
        }

        val restAdapter =
            RestAdapter<AppRolle, Transaksjonskontekst>(
                personPseudoIdProvider = personPseudoIdProvider,
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
                transaksjonProvider = transaksjon,
            )
        routing {
            get(
                GetVilkårsvurderingerForPersonBehandler(
                    SpleisOpptjeningsvurderingService(
                        object : ISpleisClient {
                            override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> = emptyList()
                        },
                    ),
                ),
                restAdapter,
            )
            post(OverstyrVilkårsvurderingBehandler(meldingskontekst = { rapid }), restAdapter)
        }
    }

    @Test
    fun `overstyrer en ikke-oppfylt automatisk vurdering til oppfylt, og resultatriveren følger etter`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
            application { settOppTestapp(pseudoIdProvider) }

            dump("0")

            // Steg 1: automatisk vurdering av en arbeidstaker med for kort opptjening (27 dager, krever 28)
            val behovId = UUID.randomUUID()
            rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)

            dump("Mens vi venter på ARBEIDSFORHOLD-BEHOVET :")

            rapid.sendTestMessage(
                arbeidsforholdløsning(behovId, arbeidsforhold(ansattSiden = "2018-01-05", ansattTil = "2018-01-31")),
                FØDSELSNUMMER,
            )
            assertEquals(2, rapid.inspektør.size)

            dump("1")

            val automatiskId =
                rapid.inspektør
                    .message(1)
                    .path("@løsning")
                    .path("Opptjeningsvurdering")
                    .path("id")
                    .asString()

            // Steg 2: REST-API-et er enig med automatikken — ikke rett til sykepenger
            val getRespons = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$automatiskId")
            assertEquals(HttpStatusCode.OK, getRespons.status)
            val getResponsBody = getRespons.bodyAsText()
            val getJson = objectMapper.readTree(getResponsBody)
            assertFalse(getJson["krav"].single()["rettTilSykepenger"].asBoolean()) {
                "Automatikken skulle kommet til at opptjeningsvilkåret ikke er oppfylt: $getResponsBody"
            }

            // Steg 3: OpptjeningsvurderingResultat-riveren sier ok=false for den automatiske vurderingen
            rapid.sendTestMessage(opptjeningsvurderingResultatBehov(UUID.fromString(automatiskId)), FØDSELSNUMMER)
            assertEquals(3, rapid.inspektør.size)
            assertFalse(
                rapid.inspektør
                    .message(2)
                    .path("@løsning")
                    .path("OpptjeningsvurderingResultat")
                    .path("ok")
                    .asBoolean(),
            )

            // Steg 4: saksbehandler overstyrer — opptjening er OK likevel, pga. likestilt ytelse
            @Language("JSON")
            val overstyringsrequest = """
            {
              "skjæringstidspunkt": "2018-02-01",
              "vilkårskode": "OPPTJENING_LIKESTILT_YTELSE",
              "utfall": "OPPFYLT",
              "fritekstbegrunnelse": "Har hatt en likestilt ytelse rett før skjæringstidspunktet"
            }
            """
            val postRespons =
                client.post("/api/personer/$pseudoId/vilkarsvurderinger/overstyring") {
                    contentType(ContentType.Application.Json)
                    setBody(overstyringsrequest)
                }
            assertEquals(HttpStatusCode.OK, postRespons.status)
            val nyId = objectMapper.readTree(postRespons.bodyAsText())["opptjeningsvurderingId"].asString()
            assertNotEquals(automatiskId, nyId) { "Overstyringen skal lage en ny kravvurdering, ikke skrive over den gamle" }

            dump("etter 4")

            // Overstyringen skal ha publisert et event til utregningsappen om den nye kravvurderingen
            assertEquals(4, rapid.inspektør.size)
            val overstyringsevent = rapid.inspektør.message(3)
            assertEquals("endret_opptjeningsvurdering", overstyringsevent.path("@event_name").asString())
            assertEquals(nyId, overstyringsevent.path("opptjeningsvurderingId").asString())
            assertEquals(FØDSELSNUMMER, overstyringsevent.path("fødselsnummer").asString())
            assertEquals("2018-02-01", overstyringsevent.path("skjæringstidspunkt").asString())
            assertTrue(overstyringsevent.path("manuellVurdering").asBoolean())

            // Steg 5: kjør OpptjeningsvurderingResultat-riveren på nytt, nå med den nye id-en — ok=true
            rapid.sendTestMessage(opptjeningsvurderingResultatBehov(UUID.fromString(nyId)), FØDSELSNUMMER)
            assertEquals(5, rapid.inspektør.size)
            assertTrue(
                rapid.inspektør
                    .message(4)
                    .path("@løsning")
                    .path("OpptjeningsvurderingResultat")
                    .path("ok")
                    .asBoolean(),
            )
        }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ORGNUMMER = "987654321"

        @Language("JSON")
        fun opptjeningsvurderingBehov(
            id: UUID,
            arbeidssituasjon: String,
        ) = """
        {
          "@event_name": "behov",
          "@id": "$id",
          "@behov": ["Opptjeningsvurdering"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "Opptjeningsvurdering" : {
              "skjæringstidspunkt": "2018-02-01",
              "arbeidssituasjon": "$arbeidssituasjon"
          }
        }
        """

        fun arbeidsforhold(
            orgnummer: String = ORGNUMMER,
            type: String = "ORDINÆRT",
            ansattSiden: String,
            ansattTil: String? = null,
        ) = """
            {
              "orgnummer": "$orgnummer",
              "type": "$type",
              "ansattSiden": "$ansattSiden",
              "ansattTil": ${ansattTil?.let { "\"$it\"" } ?: "null"}
            }
            """.trimIndent()

        @Language("JSON")
        fun arbeidsforholdløsning(
            behovId: UUID,
            vararg arbeidsforhold: String,
            erFinal: Boolean = true,
        ) = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["ArbeidsforholdV2"],
          "@final": $erFinal,
          "fødselsnummer": "$FØDSELSNUMMER",
          "skjæringstidspunkt": "2018-02-01",
          "opprinneligBehov": {
            "@event_name": "behov",
            "@id": "$behovId",
            "@behov": ["Opptjeningsvurdering"],
            "fødselsnummer": "$FØDSELSNUMMER",
            "skjæringstidspunkt": "2018-02-01",
            "arbeidssituasjon": "Arbeidstaker"
          },
          "@løsning": {
            "ArbeidsforholdV2": [${arbeidsforhold.joinToString()}]
          }
        }
        """

        @Language("JSON")
        fun opptjeningsvurderingResultatBehov(opptjeningsvurderingId: UUID) =
            """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "fødselsnummer": "$FØDSELSNUMMER",
          "@behov": ["OpptjeningsvurderingResultat"],
          "OpptjeningsvurderingResultat": {
            "opptjeningsvurderingId": "$opptjeningsvurderingId"
          }
        }
        """
    }
}
