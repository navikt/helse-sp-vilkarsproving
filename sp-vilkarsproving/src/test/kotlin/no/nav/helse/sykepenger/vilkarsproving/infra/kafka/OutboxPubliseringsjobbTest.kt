package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvoluttId
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class OutboxPubliseringsjobbTest {
    private val transaksjon = InMemoryTransaksjonProvider()

    private fun nyMelding(id: UUID = UUID.randomUUID()) =
        OutboxMelding.OpptjeningsvurderingOverstyrt(
            skjæringstidspunkt = LocalDate.of(2024, 1, 1),
            opptjeningsvurderingId = id,
            manuellVurdering = true,
        )

    @Test
    fun `plukker opp upubliserte meldinger, publiserer dem og markerer dem som publisert`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(rapid, transaksjon)
        val opptjeningsvurderingId = UUID.randomUUID()
        val melding = OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(opptjeningsvurderingId), identitetsnummer = Identitetsnummer("12029240045"))
        transaksjon.outbox.leggTil(melding)

        jobb.kjørEnRunde()

        assertEquals(1, rapid.inspektør.size)
        val publisert = rapid.inspektør.message(0)
        assertEquals("endret_opptjeningsvurdering", publisert.path("@event_name").asString())
        assertEquals(opptjeningsvurderingId.toString(), publisert.path("opptjeningsvurderingId").asString())
        assertEquals(melding.identitetsnummer.value, rapid.inspektør.key(0))
        assertTrue(transaksjon.outbox.hentUpubliserte().isEmpty())
    }

    @Test
    fun `flere meldinger publiseres i rekkefølge`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(rapid, transaksjon)
        val meldinger = (1..3).map { OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(), identitetsnummer = Identitetsnummer("12029240045")) }
        meldinger.forEach { transaksjon.outbox.leggTil(it) }

        jobb.kjørEnRunde()

        assertEquals(3, rapid.inspektør.size)
        assertTrue(transaksjon.outbox.hentUpubliserte().isEmpty())
    }

    @Test
    fun `ingen upubliserte meldinger gir ingen publisering`() {
        val rapid = TestRapid()
        val jobb = OutboxPubliseringsjobb(rapid, transaksjon)

        jobb.kjørEnRunde()

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `feil under publisering markerer ikke meldingen som publisert, og jobben kastes ikke videre`() {
        val sviktendeRapid = SvikterVedPubliseringRapid()
        val jobb = OutboxPubliseringsjobb(sviktendeRapid, transaksjon)
        val melding = OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(), identitetsnummer = Identitetsnummer("12029240045"))
        transaksjon.outbox.leggTil(melding)

        jobb.kjørEnRunde()

        assertEquals(listOf(melding), transaksjon.outbox.hentUpubliserte())
    }

    private class SvikterVedPubliseringRapid : RapidsConnection() {
        override fun publish(message: String): Unit = error("Kafka er nede")

        override fun publish(
            key: String,
            message: String,
        ): Unit = error("Kafka er nede")

        override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> = error("Kafka er nede")

        override fun rapidName() = "svikter-rapid"

        override fun start() = Unit

        override fun stop() = Unit
    }
}
