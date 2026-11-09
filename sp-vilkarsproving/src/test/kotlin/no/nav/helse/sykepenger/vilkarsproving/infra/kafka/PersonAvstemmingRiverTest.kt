package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.PersonAvstemmingService
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori.FakeRettighetskategoriClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class PersonAvstemmingRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID())
    private val rettighetskategorier = FakeRettighetskategoriClient()
    private val rapid =
        TestRapid().apply {
            PersonAvstemmingRiver(
                rapidsConnection = this,
                personAvstemmingService =
                    PersonAvstemmingService(
                        transaksjonProvider = transaksjon,
                        spleisClient =
                            object : ISpleisClient {
                                override fun hentOpptjeningsvurderinger(fødselsnummer: String) =
                                    listOf(
                                        SpleisOpptjeningsvurdering.SpleisSelvstendig(
                                            opptjeningsvurderingId = opptjeningsvurderingId,
                                            opprettet = Instant.now(),
                                            skjæringstidspunkt = LocalDate.of(2026, 1, 1),
                                            rettighetskategorier =
                                                listOf(
                                                    SpleisOpptjeningsvurdering.Rettighetskategori(
                                                        id = RETTIGHETSKATEGORI_ID,
                                                        opprettet = Instant.parse("2026-01-02T10:15:30Z"),
                                                    ),
                                                ),
                                        ),
                                    )
                            },
                    ),
            )
            OpptjeningsvurderingRiver(this, transaksjon, rettighetskategorier)
        }

    @Test
    fun `lagrer opptjeningsvurderinger ved person-avstemming`() {
        rapid.sendTestMessage(
            """
            {
              "@event_name": "person_avstemming",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$FØDSELSNUMMER"
            }
            """.trimIndent(),
        )

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            FØDSELSNUMMER,
            transaksjon.opptjeningsvurderinger.alleVurderinger
                .single()
                .fødselsnummer,
        )
    }

    @Test
    fun `hopper over melding når opptjeningsvurderingen allerede er lagret for en annen person`() {
        sendPersonAvstemming(FØDSELSNUMMER)
        sendPersonAvstemming(ANNET_FØDSELSNUMMER)

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            FØDSELSNUMMER,
            transaksjon.opptjeningsvurderinger.alleVurderinger
                .single()
                .fødselsnummer,
        )
    }

    private fun sendPersonAvstemming(fødselsnummer: String) {
        rapid.sendTestMessage(
            """
            {
              "@event_name": "person_avstemming",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$fødselsnummer"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `behov med en backfillet rettighetskategori-ID svarer med en gang`() {
        sendPersonAvstemming(FØDSELSNUMMER)

        rapid.sendTestMessage(
            """
            {
              "@event_name": "behov",
              "@id": "${UUID.randomUUID()}",
              "@behov": ["Opptjeningsvurdering"],
              "fødselsnummer": "$FØDSELSNUMMER",
              "Opptjeningsvurdering": {
                "skjæringstidspunkt": "2026-01-01",
                "arbeidssituasjon": "Arbeidstaker",
                "rettighetskategoriId": "$RETTIGHETSKATEGORI_ID"
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(1, rapid.inspektør.size)
        assertEquals(
            opptjeningsvurderingId.toString(),
            rapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Opptjeningsvurdering")
                .path("id")
                .asString(),
        )
    }

    @Test
    fun `ignorerer andre hendelser`() {
        rapid.sendTestMessage(
            """
            {
              "@event_name": "annen-hendelse",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$FØDSELSNUMMER"
            }
            """.trimIndent(),
        )

        assertEquals(0, transaksjon.opptjeningsvurderinger.antallLagringer)
    }

    private companion object {
        val RETTIGHETSKATEGORI_ID = RettighetskategoriId(UUID.fromString("7d2f1b1e-3c4a-4f5e-9a6b-1c2d3e4f5a6b"))
        const val FØDSELSNUMMER = "12029240045"
        const val ANNET_FØDSELSNUMMER = "13029240045"
    }
}
