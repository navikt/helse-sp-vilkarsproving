package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMeldingId
import no.nav.helse.sykepenger.vilkarsproving.application.UtgåendeLøsning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

internal class PostgresOutboxTest : DatabaseTest() {
    private val objectMapper = jacksonObjectMapper()

    private fun assertSammeInnhold(
        forventet: UtgåendeLøsning,
        faktisk: UtgåendeLøsning,
    ) {
        assertEquals(forventet.id, faktisk.id)
        assertEquals(forventet.fødselsnummer, faktisk.fødselsnummer)
        assertEquals(objectMapper.readTree(forventet.meldingJson), objectMapper.readTree(faktisk.meldingJson))
    }

    @Test
    fun `lagt til melding kommer tilbake som upublisert`() {
        val melding = UtgåendeLøsning(id = OutboxMeldingId.ny(), meldingJson = """{"foo":"bar"}""", fødselsnummer = "12029240045")

        transaksjon { it.outbox.leggTil(melding) }

        val upubliserte = transaksjon { it.outbox.hentUpubliserte() }
        assertSammeInnhold(melding, upubliserte.single())
    }

    @Test
    fun `publiserte meldinger dukker ikke opp igjen`() {
        val melding = UtgåendeLøsning(id = OutboxMeldingId.ny(), meldingJson = """{"foo":"bar"}""", fødselsnummer = "12029240045")
        transaksjon { it.outbox.leggTil(melding) }

        transaksjon { it.outbox.markerSomPublisert(melding.id) }

        assertTrue(transaksjon { it.outbox.hentUpubliserte() }.isEmpty())
    }

    @Test
    fun `henter kun de eldste meldingene når maksAntall er satt`() {
        val meldinger = (1..3).map { UtgåendeLøsning(id = OutboxMeldingId.ny(), meldingJson = """{"i":$it}""", fødselsnummer = "12029240045") }
        transaksjon { kontekst -> meldinger.forEach { kontekst.outbox.leggTil(it) } }

        val upubliserte = transaksjon { it.outbox.hentUpubliserte(maksAntall = 2) }

        assertEquals(2, upubliserte.size)
        meldinger.take(2).zip(upubliserte).forEach { (forventet, faktisk) -> assertSammeInnhold(forventet, faktisk) }
    }

    @Test
    fun `hentUpubliserte gir tom liste når outboxen er tom`() {
        assertTrue(transaksjon { it.outbox.hentUpubliserte() }.isEmpty())
    }
}
