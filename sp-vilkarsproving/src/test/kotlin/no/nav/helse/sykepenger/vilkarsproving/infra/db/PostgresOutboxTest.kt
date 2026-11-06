package no.nav.helse.sykepenger.vilkarsproving.infra.db

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.sykepenger.libs.leaderelection.LeaderElection
import no.nav.sykepenger.libs.outbox.OutboxPubliseringsjobb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

internal class PostgresOutboxTest : DatabaseTest() {
    private val fødselsnummer = "12029240045"
    private val rapid = TestRapid()

    @Test
    fun `meldingen publiseres uendret med fødselsnummeret som nøkkel`() {
        // Given
        val melding = melding("endret_opptjeningsvurdering")
        transaksjon { it.outbox.leggTil(fødselsnummer, melding) }

        // When
        publiserUpubliserte()

        // Then
        assertEquals(1, rapid.inspektør.size)
        assertEquals(jacksonObjectMapper().readTree(melding), rapid.inspektør.message(0))
        assertEquals(fødselsnummer, rapid.inspektør.key(0))
    }

    @Test
    fun `meldinger i samme transaksjon publiseres i innsettingsrekkefølge`() {
        // Given
        transaksjon { kontekst ->
            kontekst.outbox.leggTil(fødselsnummer, melding("saksbehandlerhandling"))
            kontekst.outbox.leggTil(fødselsnummer, melding("endret_opptjeningsvurdering"))
        }

        // When
        publiserUpubliserte()

        // Then
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
    fun `meldingen lagres ikke når transaksjonen rulles tilbake`() {
        // When
        assertThrows<IllegalStateException> {
            transaksjon { kontekst ->
                kontekst.outbox.leggTil(fødselsnummer, melding("endret_opptjeningsvurdering"))
                error("Noe feilet etter at meldingen ble lagt i outboxen")
            }
        }

        // Then
        assertEquals(0, Database.antallRader("outbox"))
    }

    private fun publiserUpubliserte() = OutboxPubliseringsjobb(Database.dataSource, rapid, LeaderElection { true }).publiserUpubliserte()

    private fun melding(eventName: String) = JsonMessage.newMessage(eventName, mapOf("fødselsnummer" to fødselsnummer, "id" to UUID.randomUUID())).toJson()
}
