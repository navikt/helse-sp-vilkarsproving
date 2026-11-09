package no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import java.time.Duration
import java.util.UUID

/** En falsk sp-rettighetskategori, med en ekte klient som snakker med den. Husk å kalle [stopp] etter testen. */
internal class RettighetskategoriWiremock {
    private val server =
        WireMockServer(wireMockConfig().dynamicPort())
            .also(WireMockServer::start)

    val client: RettighetskategoriClient =
        RettighetskategoriClient(
            scope = SCOPE,
            baseUrl = server.baseUrl(),
            tokenProvider =
                object : AccessTokenProvider {
                    override fun machineToken(scope: String) = ACCESS_TOKEN

                    override fun oboToken(
                        accessToken: String,
                        scope: String,
                    ): String = throw NotImplementedError("ikke i bruk")
                },
            ventetiderMellomForsøk = listOf(Duration.ZERO, Duration.ZERO),
        )

    /** Svarer bare når både ID-en og fødselsnummeret stemmer. Ellers svarer WireMock 404, slik sp-rettighetskategori gjør. */
    fun stubRettighetskategori(
        rettighetskategoriId: UUID,
        fødselsnummer: String,
        hovedkategori: String?,
        tilleggskategorier: List<String> = emptyList(),
    ) {
        val hovedkategorifelt = hovedkategori?.let { """"hovedkategori": "$it",""" } ?: ""
        server.stubFor(
            post(urlPathEqualTo("/api/rettighetskategorier/$rettighetskategoriId"))
                .withRequestBody(equalToJson("""{ "fødselsnummer": "$fødselsnummer" }"""))
                .willReturn(
                    okJson(
                        """
                        {
                          "id": "$rettighetskategoriId",
                          "skjæringstidspunkt": "2018-02-01",
                          $hovedkategorifelt
                          "tilleggskategorier": [ ${tilleggskategorier.joinToString { "\"$it\"" }} ],
                          "kilde": "AUTOMATISK",
                          "vurderingstidspunkt": "2018-02-01T12:00:00Z"
                        }
                        """.trimIndent(),
                    ),
                ),
        )
    }

    fun stubFeil(
        rettighetskategoriId: UUID,
        status: Int,
    ) {
        server.stubFor(
            post(urlPathEqualTo("/api/rettighetskategorier/$rettighetskategoriId"))
                .willReturn(
                    aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("""{ "type": "about:blank", "status": $status }"""),
                ),
        )
    }

    fun stubUgyldigSvar(rettighetskategoriId: UUID) {
        server.stubFor(
            post(urlPathEqualTo("/api/rettighetskategorier/$rettighetskategoriId"))
                .willReturn(okJson("ikke json")),
        )
    }

    fun stubNettverksfeil(rettighetskategoriId: UUID) {
        server.stubFor(
            post(urlPathEqualTo("/api/rettighetskategorier/$rettighetskategoriId"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )
    }

    fun loggedRequests(): List<LoggedRequest> = server.findAll(postRequestedFor(urlPathMatching("/api/rettighetskategorier/.*")))

    fun baseUrl(): String = server.baseUrl()

    fun stopp() {
        server.stop()
    }

    companion object {
        const val ACCESS_TOKEN = "et-fake-token"
        const val SCOPE = "api://test.tbd.sp-rettighetskategori/.default"
    }
}
