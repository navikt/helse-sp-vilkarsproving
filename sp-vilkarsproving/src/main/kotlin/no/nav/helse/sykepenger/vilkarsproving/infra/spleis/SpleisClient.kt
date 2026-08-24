package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.access_token.TexasClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

internal class SpleisClient(
    private val scope: String,
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun fromEnv(
            env: Map<String, String> = System.getenv(),
            tokenProvider: AccessTokenProvider = TexasClient.fromEnv(),
        ): SpleisClient {
            val prod = env["NAIS_CLUSTER_NAME"]?.startsWith("prod") ?: false
            val scope = if (prod) "api://prod-gcp.tbd.spleis-api/.default" else "api://dev-gcp.tbd.spleis-api/.default"
            val baseUrl = "http://spleis-api"
            return SpleisClient(scope, baseUrl, tokenProvider)
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class PersonRequest(
            val fødselsnummer: String,
        )
    }

    fun hentOpptjeningsvurderinger(fødselsnummer: String): List<OpptjeningsvurderingDto> {
        val m2mToken = tokenProvider.machineToken(scope)

        val body = PersonRequest(fødselsnummer)

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI("$baseUrl/api/opptjeningsvurderinger"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $m2mToken")
                .header("callId", UUID.randomUUID().toString())
                .method("POST", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw SpleisClientException("Uventet svar fra spleis-api (HTTP ${response.statusCode()}): ${response.body()}")
        }

        return objectMapper.readValue<OpptjeningsvurderingerResponse>(response.body()).opptjeningsvurderinger
    }
}

internal class SpleisClientException(
    message: String,
) : RuntimeException(message)
