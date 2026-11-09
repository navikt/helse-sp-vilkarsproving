package no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori

import com.github.navikt.tbd_libs.access_token.AccessTokenProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.Hovedkategori
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

internal class RettighetskategoriClientTest {
    private val wiremock = RettighetskategoriWiremock()
    private val client = wiremock.client
    private val rettighetskategoriId = UUID.randomUUID()

    @AfterEach
    fun tearDown() {
        wiremock.stopp()
    }

    @Test
    fun `henter hovedkategorien med ID i stien, fødselsnummer i bodyen og maskintoken`() {
        wiremock.stubRettighetskategori(rettighetskategoriId, FØDSELSNUMMER, "SELVSTENDIG_NAERINGSDRIVENDE", listOf("JORDBRUKER"))

        val hovedkategori = client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER)

        assertEquals(Hovedkategori.SelvstendigNæringsdrivende, hovedkategori)
        val request = wiremock.loggedRequests().single()
        assertEquals("Bearer ${RettighetskategoriWiremock.ACCESS_TOKEN}", request.getHeader("Authorization"))
        assertEquals(
            jacksonObjectMapper().readTree("""{ "fødselsnummer": "$FØDSELSNUMMER" }"""),
            jacksonObjectMapper().readTree(request.bodyAsString),
        )
    }

    @Test
    fun `fromEnv bruker scope og base-URL fra miljøvariablene`() {
        wiremock.stubRettighetskategori(rettighetskategoriId, FØDSELSNUMMER, "ARBEIDSTAKER")
        var brukteScope: String? = null
        val client =
            RettighetskategoriClient.fromEnv(
                env =
                    mapOf(
                        "SP_RETTIGHETSKATEGORI_SCOPE" to "api://et-miljø.tbd.sp-rettighetskategori/.default",
                        "SP_RETTIGHETSKATEGORI_BASE_URL" to wiremock.baseUrl(),
                    ),
                tokenProvider =
                    object : AccessTokenProvider {
                        override fun machineToken(scope: String) = RettighetskategoriWiremock.ACCESS_TOKEN.also { brukteScope = scope }

                        override fun oboToken(
                            accessToken: String,
                            scope: String,
                        ): String = throw NotImplementedError("ikke i bruk")
                    },
            )

        assertEquals(Hovedkategori.Arbeidstaker, client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER))
        assertEquals("api://et-miljø.tbd.sp-rettighetskategori/.default", brukteScope)
    }

    @Test
    fun `kjenner alle hovedkategoriene`() {
        val forventet =
            mapOf(
                "ARBEIDSTAKER" to Hovedkategori.Arbeidstaker,
                "SELVSTENDIG_NAERINGSDRIVENDE" to Hovedkategori.SelvstendigNæringsdrivende,
                "ARBEIDSTAKER_OG_SELVSTENDIG_NAERINGSDRIVENDE" to Hovedkategori.ArbeidstakerOgSelvstendigNæringsdrivende,
            )
        forventet.forEach { (verdi, hovedkategori) ->
            val id = UUID.randomUUID()
            wiremock.stubRettighetskategori(id, FØDSELSNUMMER, verdi)
            assertEquals(hovedkategori, client.hentHovedkategori(RettighetskategoriId(id), FØDSELSNUMMER))
        }
    }

    @Test
    fun `ukjent hovedkategori gir Ukjent med verdien`() {
        wiremock.stubRettighetskategori(rettighetskategoriId, FØDSELSNUMMER, "FRILANSER")

        assertEquals(Hovedkategori.Ukjent("FRILANSER"), client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER))
    }

    @Test
    fun `manglende hovedkategori gir Ukjent uten verdi`() {
        wiremock.stubRettighetskategori(rettighetskategoriId, FØDSELSNUMMER, hovedkategori = null)

        assertEquals(Hovedkategori.Ukjent(null), client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER))
    }

    @ParameterizedTest
    @ValueSource(ints = [500, 502, 503])
    fun `prøver tre ganger ved 5xx og gir så opp`(status: Int) {
        wiremock.stubFeil(rettighetskategoriId, status)

        assertThrows<RettighetskategoriClientException> {
            client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER)
        }
        assertEquals(3, wiremock.loggedRequests().size)
    }

    @Test
    fun `prøver tre ganger ved nettverksfeil og gir så opp`() {
        wiremock.stubNettverksfeil(rettighetskategoriId)

        assertThrows<RettighetskategoriClientException> {
            client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER)
        }
        assertEquals(3, wiremock.loggedRequests().size)
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404])
    fun `gir opp med en gang ved 4xx`(status: Int) {
        wiremock.stubFeil(rettighetskategoriId, status)

        assertThrows<RettighetskategoriClientException> {
            client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER)
        }
        assertEquals(1, wiremock.loggedRequests().size)
    }

    @Test
    fun `ugyldig svar gir RettighetskategoriClientException`() {
        wiremock.stubUgyldigSvar(rettighetskategoriId)

        assertThrows<RettighetskategoriClientException> {
            client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER)
        }
    }

    @Test
    fun `feil ved henting av token gir RettighetskategoriClientException`() {
        val client =
            RettighetskategoriClient(
                scope = RettighetskategoriWiremock.SCOPE,
                baseUrl = "http://localhost:1",
                tokenProvider =
                    object : AccessTokenProvider {
                        override fun machineToken(scope: String): String = error("Texas er nede")

                        override fun oboToken(
                            accessToken: String,
                            scope: String,
                        ): String = throw NotImplementedError("ikke i bruk")
                    },
            )

        assertThrows<RettighetskategoriClientException> {
            client.hentHovedkategori(RettighetskategoriId(rettighetskategoriId), FØDSELSNUMMER)
        }
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
    }
}
