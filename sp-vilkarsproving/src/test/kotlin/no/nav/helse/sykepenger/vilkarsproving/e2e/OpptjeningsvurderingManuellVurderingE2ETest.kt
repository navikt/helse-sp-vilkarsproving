package no.nav.helse.sykepenger.vilkarsproving.e2e

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.helse.sykepenger.vilkarsproving.application.PersonAvstemmingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.db.Database
import no.nav.helse.sykepenger.vilkarsproving.infra.db.DatabaseTest
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OutboxPubliseringsjobb
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.PostManuellArbeidMinstFireUkerVurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.PostManuellLikestiltYtelseVurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.PostManuellOpptjeningsvurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.*
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.get
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.post
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

internal class OpptjeningsvurderingManuellVurderingE2ETest : DatabaseTest() {
    /** Sett til `true` for å dumpe innholdet i databasetabellene mellom hvert steg, se [dump]. */
    private val dump = true

    private fun dump(steg: String) {
        if (!dump) return
        Database.dump(
            "opptjeningsproving",
            "opptjeningsvurdering",
            "vilkarsvurdering",
            "opptjeningsvurdering_vilkarsvurdering",
            overskrift = steg
        )
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
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> =
                        emptyList()
                },
            )
        }

    private val outboxJobb = OutboxPubliseringsjobb(rapid, transaksjon, { true })

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
                AccessToken("token"),
            )
        intercept(ApplicationCallPipeline.Plugins) {
            call.authentication.principal(principal)
        }

        val restAdapter =
            RestAdapter<Transaksjonskontekst>(
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
                transaksjonProvider = transaksjon,
            )
        routing {
            get(
                GetVilkårsvurderingerForPersonBehandler(
                    PersonAvstemmingService(
                        transaksjon,
                        object : ISpleisClient {
                            override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> =
                                emptyList()
                        },
                    ),
                ),
                restAdapter,
            )
            post(PostManuellArbeidMinstFireUkerVurderingBehandler(), restAdapter)
            post(PostManuellLikestiltYtelseVurderingBehandler(), restAdapter)
            post(PostManuellOpptjeningsvurderingBehandler(), restAdapter)
        }
    }

    @Test
    fun `vurderer en ikke-oppfylt automatisk vurdering manuelt til oppfylt, og resultatriveren følger etter`() =
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
            val getRespons =
                client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$automatiskId")
            assertEquals(HttpStatusCode.OK, getRespons.status)
            val getResponsBody = getRespons.bodyAsText()
            val getJson = objectMapper.readTree(getResponsBody)
            assertFalse(getJson["krav"].single()["opptjeningOk"].asBoolean()) {
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

            // Steg 4: saksbehandler vurderer manuelt — opptjening er OK likevel, pga. likestilt ytelse
            @Language("JSON")
            val manuellVurderingRequest = """
            {
              "skjæringstidspunkt": "2018-02-01",
              "utfall": "OPPFYLT",
              "fritekstbegrunnelse": "Mangelfulle opplysninger i registeret, har likevel tilstrekkelig opptjening",
              "journalpostId": ["journalpost-1", "journalpost-2"]
            }
            """
            val postRespons =
                client.post("/api/personer/$pseudoId/vilkarsvurderinger/manuell/arbeid-minst-fire-uker") {
                    contentType(ContentType.Application.Json)
                    setBody(manuellVurderingRequest)
                }
            assertEquals(HttpStatusCode.OK, postRespons.status)
            val nyId = objectMapper.readTree(postRespons.bodyAsText())["opptjeningsvurderingId"].asString()
            assertNotEquals(
                automatiskId,
                nyId
            ) { "Den manuelle vurderingen skal lage en ny opptjeningsvurdering, ikke skrive over den gamle" }

            outboxJobb.kjørEnRundeHvisLeader()

            dump("etter 4")

            // Den nye vurderingen skal vise helheten: den automatiske 4-ukers-vurderingen er med, den manuelle vurderingen er avgjørende
            val getEtterManuellVurdering =
                client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$nyId")
            assertEquals(HttpStatusCode.OK, getEtterManuellVurdering.status)
            val nyOpptjeningsvurdering = objectMapper.readTree(getEtterManuellVurdering.bodyAsText())["krav"].single()
            assertTrue(nyOpptjeningsvurdering["opptjeningOk"].asBoolean())
            assertEquals("OPPTJENING_ARBEID_MINST_4_UKER", nyOpptjeningsvurdering["avgjørendeVilkårskode"].asString())
            assertEquals(
                listOf("OPPTJENING_ARBEID_MINST_4_UKER"),
                nyOpptjeningsvurdering["vurderinger"].toList().map { it["vilkårskode"].asString() },
            )
            assertEquals("OPPFYLT", nyOpptjeningsvurdering["vurderinger"][0]["utfall"].asString())
            assertEquals(
                listOf("journalpost-1", "journalpost-2"),
                nyOpptjeningsvurdering["vurderinger"]
                    .toList()
                    .single()["kilde"]["journalpostId"]
                    .toList()
                    .map { it.asString() },
            )

            // Den manuelle vurderingen skal ha publisert et event til utregningsappen om den nye opptjeningsvurderingen, i tillegg til et event som forteller om saksbehandlerendringen
            assertEquals(5, rapid.inspektør.size)
            val manuelleHendelser = (3..4).map { rapid.inspektør.message(it) }
            assertEquals(
                setOf("endret_opptjeningsvurdering", "saksbehandlerhandling"),
                manuelleHendelser.map { it.path("@event_name").asString() }.toSet(),
            )
            val manuellVurderingEvent =
                manuelleHendelser.single { it.path("@event_name").asString() == "endret_opptjeningsvurdering" }
            assertEquals(nyId, manuellVurderingEvent.path("opptjeningsvurderingId").asString())
            assertEquals(FØDSELSNUMMER, manuellVurderingEvent.path("fødselsnummer").asString())
            assertEquals("2018-02-01", manuellVurderingEvent.path("skjæringstidspunkt").asString())
            assertTrue(manuellVurderingEvent.path("manuellVurdering").asBoolean())

            // Steg 5: kjør OpptjeningsvurderingResultat-riveren på nytt, nå med den nye id-en — ok=true
            rapid.sendTestMessage(opptjeningsvurderingResultatBehov(UUID.fromString(nyId)), FØDSELSNUMMER)
            assertEquals(6, rapid.inspektør.size)
            assertTrue(
                rapid.inspektør
                    .message(5)
                    .path("@løsning")
                    .path("OpptjeningsvurderingResultat")
                    .path("ok")
                    .asBoolean(),
            )
        }

    @Test
    fun `publiserer saksbehandlerhandling før endret opptjeningsvurdering`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
            application { settOppTestapp(pseudoIdProvider) }

            @Language("JSON")
            val manuellVurderingRequest = """
            {
              "skjæringstidspunkt": "2018-02-01",
              "utfall": "OPPFYLT",
              "fritekstbegrunnelse": "Manuell vurdering for test av eventrekkefølge",
              "journalpostId": ["journalpost-1"]
            }
            """
            val postRespons =
                client.post("/api/personer/$pseudoId/vilkarsvurderinger/manuell/arbeid-minst-fire-uker") {
                    contentType(ContentType.Application.Json)
                    setBody(manuellVurderingRequest)
                }
            assertEquals(HttpStatusCode.OK, postRespons.status)

            outboxJobb.kjørEnRundeHvisLeader()

            assertEquals(
                listOf("saksbehandlerhandling", "endret_opptjeningsvurdering"),
                (0 until rapid.inspektør.size).map {
                    rapid.inspektør
                        .message(it)
                        .path("@event_name")
                        .asString()
                },
            )
        }

    @Test
    fun `foreldrepenger opptjent på AAP med yrkesaktivitet før uttak gir rett etter tredje punktum`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
            application { settOppTestapp(pseudoIdProvider) }

            // Automatisk vurdering med for kort opptjening (27 dager, krever 28)
            val behovId = UUID.randomUUID()
            rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)
            rapid.sendTestMessage(
                arbeidsforholdløsning(behovId, arbeidsforhold(ansattSiden = "2018-01-05", ansattTil = "2018-01-31")),
                FØDSELSNUMMER,
            )

            @Language("JSON")
            val request = """
            {
              "skjæringstidspunkt": "2018-02-01",
              "sammenlagtMinst4Uker": true,
              "ytelser": ["FORELDREPENGER"],
              "foreldrepengerOpptjentPåAap": true,
              "yrkesaktivMinst4UkerFørUttak": true,
              "fritekstbegrunnelse": "Foreldrepenger opptjent på AAP, men yrkesaktiv i 4 uker før uttaket",
              "journalpostId": []
            }
            """
            val postRespons =
                client.post("/api/personer/$pseudoId/vilkarsvurderinger/manuell/likestilte-ytelser") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            assertEquals(HttpStatusCode.OK, postRespons.status)
            val nyId = objectMapper.readTree(postRespons.bodyAsText())["opptjeningsvurderingId"].asString()

            val krav =
                objectMapper
                    .readTree(
                        client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$nyId")
                            .bodyAsText()
                    )["krav"]
                    .single()
            assertTrue(krav["opptjeningOk"].asBoolean())
            assertEquals(
                "OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER",
                krav["avgjørendeVilkårskode"].asString()
            )
            assertEquals(
                mapOf(
                    "OPPTJENING_ARBEID_MINST_4_UKER" to "IKKE_OPPFYLT",
                    "OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER" to "OPPFYLT",
                    "OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP" to "IKKE_OPPFYLT",
                    "OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER" to "OPPFYLT",
                ),
                krav["vurderinger"].toList().associate { it["vilkårskode"].asString() to it["utfall"].asString() },
            )

            outboxJobb.kjørEnRundeHvisLeader()
            val antallFør = rapid.inspektør.size
            rapid.sendTestMessage(opptjeningsvurderingResultatBehov(UUID.fromString(nyId)), FØDSELSNUMMER)
            assertTrue(
                rapid.inspektør
                    .message(antallFør)
                    .path("@løsning")
                    .path("OpptjeningsvurderingResultat")
                    .path("ok")
                    .asBoolean(),
            )
        }

    @Test
    fun `samlet vurdering med bare arbeid erstatter tidligere vurderinger etter andre ledd`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
            application { settOppTestapp(pseudoIdProvider) }

            val behovId = UUID.randomUUID()
            rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)
            rapid.sendTestMessage(
                arbeidsforholdløsning(behovId, arbeidsforhold(ansattSiden = "2018-01-05", ansattTil = "2018-01-31")),
                FØDSELSNUMMER,
            )

            val førsteRespons =
                postSamletVurdering(
                    pseudoId,
                    opptjeningsaktiviteter = """["ARBEID", "FORELDREPENGER"]""",
                    foreldrepengerOpptjentPåAap = "true",
                    yrkesaktiv = "true",
                )
            assertEquals(HttpStatusCode.OK, førsteRespons.status)
            assertEquals(
                mapOf(
                    "OPPTJENING_ARBEID_MINST_4_UKER" to "IKKE_OPPFYLT",
                    "OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER" to "OPPFYLT",
                    "OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP" to "IKKE_OPPFYLT",
                    "OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER" to "OPPFYLT",
                ),
                hentKrav(pseudoId, førsteRespons)["vurderinger"].toList().associate { it["vilkårskode"].asString() to it["utfall"].asString() },
            )

            val andreRespons = postSamletVurdering(pseudoId, opptjeningsaktiviteter = """["ARBEID"]""")
            assertEquals(HttpStatusCode.OK, andreRespons.status)
            val krav = hentKrav(pseudoId, andreRespons)
            assertTrue(krav["opptjeningOk"].asBoolean())
            assertEquals("OPPTJENING_ARBEID_MINST_4_UKER", krav["avgjørendeVilkårskode"].asString())
            assertEquals(
                mapOf("OPPTJENING_ARBEID_MINST_4_UKER" to "OPPFYLT"),
                krav["vurderinger"].toList().associate { it["vilkårskode"].asString() to it["utfall"].asString() },
            )
            assertEquals(
                listOf("ARBEID"),
                krav["vurderinger"].single()["kilde"]["opptjeningsaktiviteter"].toList().map { it.asString() },
            )
        }

    @Test
    fun `samlet vurdering med ikke minst 4 uker avvises inntil videre`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
            application { settOppTestapp(pseudoIdProvider) }

            val respons = postSamletVurdering(pseudoId, minst4Uker = false, opptjeningsaktiviteter = "[]")

            assertEquals(HttpStatusCode.BadRequest, respons.status)
        }

    private suspend fun ApplicationTestBuilder.postSamletVurdering(
        pseudoId: PersonPseudoId,
        minst4Uker: Boolean = true,
        opptjeningsaktiviteter: String,
        foreldrepengerOpptjentPåAap: String = "null",
        yrkesaktiv: String = "null",
    ): HttpResponse {
        @Language("JSON")
        val request = """
        {
          "skjæringstidspunkt": "2018-02-01",
          "minst4Uker": $minst4Uker,
          "opptjeningsaktiviteter": $opptjeningsaktiviteter,
          "foreldrepengerOpptjentPåAap": $foreldrepengerOpptjentPåAap,
          "yrkesaktivMinst4UkerFørUttak": $yrkesaktiv,
          "fritekstbegrunnelse": "Samlet vurdering",
          "journalpostId": []
        }
        """
        return client.post("/api/personer/$pseudoId/vilkarsvurderinger/manuell/opptjening") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    private suspend fun ApplicationTestBuilder.hentKrav(
        pseudoId: PersonPseudoId,
        postRespons: HttpResponse,
    ): JsonNode {
        val id = objectMapper.readTree(postRespons.bodyAsText())["opptjeningsvurderingId"].asString()
        return objectMapper
            .readTree(client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$id").bodyAsText())["krav"]
            .single()
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
