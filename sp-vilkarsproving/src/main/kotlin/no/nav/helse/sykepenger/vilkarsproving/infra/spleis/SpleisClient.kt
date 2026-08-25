package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import com.github.navikt.tbd_libs.access_token.TexasClient
import no.nav.helse.hendelser.til
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.Opptjeningsvurdering.SpleisArbeidstaker.Ansettelsesperiode
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.Opptjeningsvurdering.SpleisArbeidstaker.Arbeidsforhold
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

internal interface ISpleisClient {
    fun hentOpptjeningsvurderinger(fødselsnummer: String): List<Opptjeningsvurdering>
}

internal class SpleisClient(
    private val scope: String,
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : ISpleisClient {
    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<Opptjeningsvurdering> {
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

        return objectMapper.readValue<OpptjeningsvurderingerResponse>(response.body()).tilDomene()
    }

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

        /**
         * Responsen fra `POST /api/opptjeningsvurderinger` i spleis-api.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class OpptjeningsvurderingerResponse(
            val opptjeningsvurderinger: List<OpptjeningsvurderingDto>,
        ) {
            fun tilDomene(): List<Opptjeningsvurdering> = opptjeningsvurderinger.map { it.tilDomene() }
        }

        /**
         * Rå JSON-representasjon av en opptjeningsvurdering fra spleis-api, slik den kommer over
         * ledningen. Dette er utelukkende et internt format for klienten: den mappes videre til
         * [Opptjeningsvurdering], som kun tillater de gyldige kombinasjonene av type og kilde.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class OpptjeningsvurderingDto(
            val opptjeningsvurderingId: VurderingId,
            val type: OpptjeningsvurderingTypeDto,
            val skjæringstidspunkt: LocalDate,
            val kilde: OpptjeningsvurderingKildeDto,
            // Følgende felter er kun satt når kilden er SPLEIS og typen er ARBEIDSTAKER. I alle andre
            // gyldige kombinasjoner har vi ingen kjennskap til om, eller hvordan, opptjeningsvilkåret
            // ble vurdert.
            val oppfylt: Boolean? = null,
            val antallDager: Int? = null,
            val opptjeningsperiode: PeriodeDto? = null,
            val arbeidsforhold: List<ArbeidsforholdDto> = emptyList(),
        ) {
            fun tilDomene(): Opptjeningsvurdering =
                when (kilde to type) {
                    OpptjeningsvurderingKildeDto.SPLEIS to OpptjeningsvurderingTypeDto.ARBEIDSTAKER ->
                        Opptjeningsvurdering.SpleisArbeidstaker(
                            opptjeningsvurderingId = opptjeningsvurderingId,
                            skjæringstidspunkt = skjæringstidspunkt,
                            oppfylt = requireNotNull(oppfylt) { "oppfylt mangler for SPLEIS/ARBEIDSTAKER-vurdering $opptjeningsvurderingId" },
                            antallDager = requireNotNull(antallDager) { "antallDager mangler for SPLEIS/ARBEIDSTAKER-vurdering $opptjeningsvurderingId" },
                            opptjeningsperiode = opptjeningsperiode?.tilDomene(),
                            arbeidsforhold = arbeidsforhold.map { it.tilDomene() },
                        )

                    OpptjeningsvurderingKildeDto.SPLEIS to OpptjeningsvurderingTypeDto.SELVSTENDIG ->
                        Opptjeningsvurdering.SpleisSelvstendig(
                            opptjeningsvurderingId = opptjeningsvurderingId,
                            skjæringstidspunkt = skjæringstidspunkt,
                        )

                    OpptjeningsvurderingKildeDto.INFOTRYGD to OpptjeningsvurderingTypeDto.ARBEIDSTAKER ->
                        Opptjeningsvurdering.InfotrygdArbeidstaker(
                            opptjeningsvurderingId = opptjeningsvurderingId,
                            skjæringstidspunkt = skjæringstidspunkt,
                        )

                    else -> error("Ugyldig kombinasjon av kilde ($kilde) og type ($type) for opptjeningsvurdering $opptjeningsvurderingId")
                }
        }

        private enum class OpptjeningsvurderingTypeDto {
            ARBEIDSTAKER,
            SELVSTENDIG,
        }

        private enum class OpptjeningsvurderingKildeDto {
            INFOTRYGD,
            SPLEIS,
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class PeriodeDto(
            val fom: LocalDate,
            val tom: LocalDate,
        ) {
            fun tilDomene() = fom til tom
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class ArbeidsforholdDto(
            val organisasjonsnummer: String,
            val ansettelsesperioder: List<AnsettelsesperiodeDto>,
        ) {
            fun tilDomene() =
                Arbeidsforhold(
                    organisasjonsnummer = organisasjonsnummer,
                    ansettelsesperioder = ansettelsesperioder.map { it.tilDomene() },
                )
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class AnsettelsesperiodeDto(
            val fom: LocalDate,
            val tom: LocalDate?,
        ) {
            fun tilDomene() = Ansettelsesperiode(fom = fom, tom = tom)
        }
    }
}

internal class SpleisClientException(
    message: String,
) : RuntimeException(message)
