package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvoluttId
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class PostgresOutboxTest : DatabaseTest() {
    private fun nyMelding(manuellVurdering: Boolean = true) =
        OutboxMelding.OpptjeningsvurderingEndret(
            skjæringstidspunkt = LocalDate.of(2024, 1, 1),
            opptjeningsvurderingId = UUID.randomUUID(),
            manuellVurdering = manuellVurdering,
        )

    private fun assertSammeInnhold(
        forventet: OutboxKonvolutt,
        faktisk: OutboxKonvolutt,
    ) {
        assertEquals(forventet.id, faktisk.id)
        assertEquals(forventet.identitetsnummer, faktisk.identitetsnummer)
        assertEquals(forventet.melding, faktisk.melding)
    }

    @Test
    fun `lagt til melding kommer tilbake som upublisert`() {
        val melding = OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(), identitetsnummer = Identitetsnummer("12029240045"))

        transaksjon { it.outbox.leggTil(melding) }

        val upubliserte = transaksjon { it.outbox.hentUpubliserte() }
        assertSammeInnhold(melding, upubliserte.single())
    }

    @Test
    fun `publiserte meldinger dukker ikke opp igjen`() {
        val melding = OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(), identitetsnummer = Identitetsnummer("12029240045"))
        transaksjon { it.outbox.leggTil(melding) }

        transaksjon { it.outbox.markerSomSendt(melding.id) }

        assertTrue(transaksjon { it.outbox.hentUpubliserte() }.isEmpty())
    }

    @Test
    fun `henter kun de eldste meldingene når maksAntall er satt`() {
        val meldinger = (1..3).map { OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(manuellVurdering = it % 2 == 0), identitetsnummer = Identitetsnummer("12029240045")) }
        transaksjon { kontekst -> meldinger.forEach { kontekst.outbox.leggTil(it) } }

        val upubliserte = transaksjon { it.outbox.hentUpubliserte(maksAntall = 2) }

        assertEquals(2, upubliserte.size)
        meldinger.take(2).zip(upubliserte).forEach { (forventet, faktisk) -> assertSammeInnhold(forventet, faktisk) }
    }

    @Test
    fun `hentUpubliserte gir tom liste når outboxen er tom`() {
        assertTrue(transaksjon { it.outbox.hentUpubliserte() }.isEmpty())
    }

    @Test
    fun `meldingen lagres med typediskriminatoren OPPTJENINGSVURDERING_ENDRET`() {
        val melding = OutboxKonvolutt(id = OutboxKonvoluttId.ny(), melding = nyMelding(), identitetsnummer = Identitetsnummer("12029240045"))

        transaksjon { it.outbox.leggTil(melding) }

        assertEquals(listOf("OPPTJENINGSVURDERING_ENDRET"), Database.meldingstyperIOutbox())
    }
}
