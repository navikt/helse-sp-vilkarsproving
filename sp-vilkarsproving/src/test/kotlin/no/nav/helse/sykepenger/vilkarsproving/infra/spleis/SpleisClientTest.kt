package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering.SpleisArbeidstaker.Ansettelsesperiode
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering.SpleisArbeidstaker.Arbeidsforhold
import no.nav.helse.til
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.*

internal class SpleisClientTest {
    private val server = WireMockServer(wireMockConfig().dynamicPort())
    private val scope = "api://dev-gcp.tbd.spleis-api/.default"
    private val fakeToken = "et-fake-token"
    private val klokka12 = Instant.parse("2026-09-29T10:00:00Z") // Oslo
    private val klokka11 = Instant.parse("2026-09-29T09:00:00Z") // Oslo
    private val tokenProvider =
        object : AccessTokenProvider {
            override fun machineToken(scope: String) = fakeToken

            override fun oboToken(
                accessToken: String,
                scope: String,
            ): String = throw NotImplementedError("ikke i bruk i denne testen")
        }
    private lateinit var client: SpleisClient

    @BeforeEach
    fun setUp() {
        server.start()
        client =
            SpleisClient(
                scope = scope,
                baseUrl = server.baseUrl(),
                tokenProvider = tokenProvider,
                timeout = Duration.ofMillis(200),
                ventetidMellomForsøk = Duration.ZERO,
            )
    }

    @AfterEach
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `prøver på nytt ved timeout`() {
        server.stubFor(
            post(urlEqualTo("/api/opptjeningsvurderinger"))
                .inScenario("timeout")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000).withBody(enRespons))
                .willSetStateTo("svarer"),
        )
        server.stubFor(
            post(urlEqualTo("/api/opptjeningsvurderinger"))
                .inScenario("timeout")
                .whenScenarioStateIs("svarer")
                .willReturn(aResponse().withStatus(200).withBody(enRespons)),
        )

        val opptjeningsvurderinger = client.hentOpptjeningsvurderinger("11111111111")

        assertEquals(4, opptjeningsvurderinger.size)
        server.verify(2, postRequestedFor(urlEqualTo("/api/opptjeningsvurderinger")))
    }

    @Test
    fun `gir opp etter maks antall forsøk ved timeout`() {
        server.stubFor(
            post(urlEqualTo("/api/opptjeningsvurderinger"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000).withBody(enRespons)),
        )

        assertThrows<HttpTimeoutException> {
            client.hentOpptjeningsvurderinger("11111111111")
        }
        server.verify(3, postRequestedFor(urlEqualTo("/api/opptjeningsvurderinger")))
    }

    @Test
    fun `henter og tolker opptjeningsvurderinger fra spleis`() {
        server.stubFor(
            post(urlEqualTo("/api/opptjeningsvurderinger"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(enRespons),
                ),
        )

        val opptjeningsvurderinger = client.hentOpptjeningsvurderinger("11111111111")

        assertEquals(4, opptjeningsvurderinger.size)

        val infotrygdvurdering = opptjeningsvurderinger[0]
        assertEquals(OpptjeningsvurderingId(UUID.fromString("b89e2ae5-59e3-388e-98cd-42a8e7350773")), infotrygdvurdering.opptjeningsvurderingId)
        assertEquals(LocalDate.of(2018, 1, 1), infotrygdvurdering.skjæringstidspunkt)
        assertTrue(infotrygdvurdering is SpleisOpptjeningsvurdering.InfotrygdArbeidstaker)
        assertEquals(klokka12, infotrygdvurdering.opprettet)

        val ikkeOppfyltSpleisvurdering = opptjeningsvurderinger[1] as SpleisOpptjeningsvurdering.SpleisArbeidstaker
        assertEquals(false, ikkeOppfyltSpleisvurdering.oppfylt)
        assertEquals(0, ikkeOppfyltSpleisvurdering.antallDager)
        assertNull(ikkeOppfyltSpleisvurdering.opptjeningsperiode)
        assertEquals(emptyList<Arbeidsforhold>(), ikkeOppfyltSpleisvurdering.arbeidsforhold)
        assertEquals(klokka12, ikkeOppfyltSpleisvurdering.opprettet)

        val oppfyltSpleisvurdering = opptjeningsvurderinger[2] as SpleisOpptjeningsvurdering.SpleisArbeidstaker
        assertEquals(true, oppfyltSpleisvurdering.oppfylt)
        assertEquals(365, oppfyltSpleisvurdering.antallDager)
        assertEquals(LocalDate.of(2017, 4, 1) til LocalDate.of(2018, 3, 31), oppfyltSpleisvurdering.opptjeningsperiode)
        assertEquals(
            listOf(Arbeidsforhold("987654322", listOf(Ansettelsesperiode(LocalDate.of(2017, 4, 1), null)))),
            oppfyltSpleisvurdering.arbeidsforhold,
        )
        assertEquals(klokka11, oppfyltSpleisvurdering.opprettet)

        val selvstendigvurdering = opptjeningsvurderinger[3]
        assertEquals(LocalDate.of(2018, 4, 1), selvstendigvurdering.skjæringstidspunkt)
        assertTrue(selvstendigvurdering is SpleisOpptjeningsvurdering.SpleisSelvstendig)
        assertEquals(klokka12, selvstendigvurdering.opprettet)

        server.verify(
            postRequestedFor(urlEqualTo("/api/opptjeningsvurderinger"))
                .withHeader("Authorization", equalTo("Bearer $fakeToken"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withRequestBody(equalToJson("""{"fødselsnummer":"11111111111"}""")),
        )
    }

    @Test
    fun `kaster exception ved feilrespons fra spleis`() {
        server.stubFor(
            post(urlEqualTo("/api/opptjeningsvurderinger"))
                .willReturn(aResponse().withStatus(500).withBody("noe gikk galt")),
        )

        assertThrows<SpleisClientException> {
            client.hentOpptjeningsvurderinger("11111111111")
        }
    }

    @Test
    fun `kaster exception ved ugyldig kombinasjon av kilde og type`() {
        server.stubFor(
            post(urlEqualTo("/api/opptjeningsvurderinger"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "opptjeningsvurderinger": [
                                {
                                  "opptjeningsvurderingId": "b89e2ae5-59e3-388e-98cd-42a8e7350773",
                                  "opprettet": "$klokka12",
                                  "type": "SELVSTENDIG",
                                  "skjæringstidspunkt": "2018-01-01",
                                  "kilde": "INFOTRYGD"
                                }
                              ]
                            }
                            """.trimIndent(),
                        ),
                ),
        )

        assertThrows<IllegalStateException> {
            client.hentOpptjeningsvurderinger("11111111111")
        }
    }

    @Language("JSON")
    private val enRespons = """
            {
              "opptjeningsvurderinger": [
                {
                  "opptjeningsvurderingId": "b89e2ae5-59e3-388e-98cd-42a8e7350773",
                  "opprettet": "2026-09-29T12:00:00.000000",                  
                  "type": "ARBEIDSTAKER",
                  "skjæringstidspunkt": "2018-01-01",
                  "kilde": "INFOTRYGD"
                },
                {
                  "opptjeningsvurderingId": "00000000-0000-0000-0000-000000000001",
                  "opprettet": "2026-09-29T12:00:00.000000",                  
                  "type": "ARBEIDSTAKER",
                  "skjæringstidspunkt": "2018-04-01",
                  "kilde": "SPLEIS",
                  "oppfylt": false,
                  "antallDager": 0,
                  "opptjeningsperiode": null,
                  "arbeidsforhold": []
                },
                {
                  "opptjeningsvurderingId": "00000000-0000-0000-0000-000000000002",
                  "opprettet": "2026-09-29T11:00:00.000000",                  
                  "type": "ARBEIDSTAKER",
                  "skjæringstidspunkt": "2018-04-01",
                  "kilde": "SPLEIS",
                  "arbeidsforhold": [
                    {
                      "organisasjonsnummer": "987654322",
                      "ansettelsesperioder": [
                        {
                          "fom": "2017-04-01",
                          "tom": null
                        }
                      ]
                    }
                  ],
                  "opptjeningsperiode": {
                    "fom": "2017-04-01",
                    "tom": "2018-03-31"
                  },
                  "oppfylt": true,
                  "antallDager": 365
                },
                {
                  "opptjeningsvurderingId": "00000000-0000-0000-0000-000000000003",
                  "opprettet": "2026-09-29T12:00:00.000000",                  
                  "skjæringstidspunkt": "2018-04-01",
                  "kilde": "SPLEIS",
                  "type": "SELVSTENDIG"
                }                
              ]
            }
            """
}
