package no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.access_token.TexasClient
import no.nav.helse.sykepenger.vilkarsproving.domain.Hovedkategori
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import no.nav.sykepenger.libs.logging.loggWarn
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

internal interface IRettighetskategoriClient {
    /** @throws RettighetskategoriClientException når oppslaget feiler */
    fun hentHovedkategori(
        rettighetskategoriId: RettighetskategoriId,
        fødselsnummer: String,
    ): Hovedkategori
}

internal class RettighetskategoriClient(
    private val scope: String,
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val timeout: Duration = Duration.ofSeconds(10),
    private val ventetiderMellomForsøk: List<Duration> = listOf(Duration.ofMillis(200), Duration.ofMillis(500)),
) : IRettighetskategoriClient {
    private val maksAntallForsøk = ventetiderMellomForsøk.size + 1

    override fun hentHovedkategori(
        rettighetskategoriId: RettighetskategoriId,
        fødselsnummer: String,
    ): Hovedkategori {
        val body =
            try {
                sendMedRetry(lagRequest(rettighetskategoriId, fødselsnummer))
            } catch (ex: RettighetskategoriClientException) {
                throw ex
            } catch (ex: Exception) {
                throw RettighetskategoriClientException("Kall mot sp-rettighetskategori feilet", ex)
            }
        val hovedkategori =
            try {
                objectMapper.readValue<RettighetskategoriResponse>(body).hovedkategori
            } catch (ex: JacksonException) {
                throw RettighetskategoriClientException("Klarte ikke å lese svaret fra sp-rettighetskategori", ex)
            }
        return when (hovedkategori) {
            "ARBEIDSTAKER" -> Hovedkategori.Arbeidstaker
            "SELVSTENDIG_NAERINGSDRIVENDE" -> Hovedkategori.SelvstendigNæringsdrivende
            "ARBEIDSTAKER_OG_SELVSTENDIG_NAERINGSDRIVENDE" -> Hovedkategori.ArbeidstakerOgSelvstendigNæringsdrivende
            else -> Hovedkategori.Ukjent(hovedkategori)
        }
    }

    private fun lagRequest(
        rettighetskategoriId: RettighetskategoriId,
        fødselsnummer: String,
    ): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(URI("$baseUrl/api/rettighetskategorier/$rettighetskategoriId"))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${tokenProvider.machineToken(scope)}")
            .header("callId", UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(mapOf("fødselsnummer" to fødselsnummer))))
            .build()

    private fun sendMedRetry(request: HttpRequest): String {
        var forsøk = 1
        while (true) {
            val feil =
                try {
                    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                    when (response.statusCode()) {
                        200 -> return response.body()
                        in 500..599 -> RettighetskategoriClientException("sp-rettighetskategori svarte HTTP ${response.statusCode()}")
                        else -> throw RettighetskategoriClientException("sp-rettighetskategori svarte HTTP ${response.statusCode()}")
                    }
                } catch (ex: IOException) {
                    RettighetskategoriClientException("Kall mot sp-rettighetskategori feilet", ex)
                }
            if (forsøk >= maksAntallForsøk) throw feil
            loggWarn(
                "Kall mot sp-rettighetskategori feilet, prøver igjen",
                feil,
                "forsøk" to forsøk.toString(),
                "maksAntallForsøk" to maksAntallForsøk.toString(),
            )
            Thread.sleep(ventetiderMellomForsøk[forsøk - 1])
            forsøk++
        }
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            tokenProvider: AccessTokenProvider = TexasClient.fromEnv(),
        ): RettighetskategoriClient =
            RettighetskategoriClient(
                scope = env.getValue("SP_RETTIGHETSKATEGORI_SCOPE"),
                baseUrl = env.getValue("SP_RETTIGHETSKATEGORI_BASE_URL"),
                tokenProvider = tokenProvider,
            )

        /** Svaret fra `POST /api/rettighetskategorier/{id}`. Opptjening leser bare hovedkategorien. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class RettighetskategoriResponse(
            val hovedkategori: String? = null,
        )
    }
}

internal class RettighetskategoriClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
