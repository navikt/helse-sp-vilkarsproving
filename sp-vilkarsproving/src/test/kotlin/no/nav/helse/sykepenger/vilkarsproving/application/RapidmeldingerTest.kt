package no.nav.helse.sykepenger.vilkarsproving.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class RapidmeldingerTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `saksbehandlerhandling har feltene spesialist trenger`() {
        val handlingId = UUID.randomUUID()

        val melding =
            objectMapper.readTree(
                saksbehandlerhandling(
                    fødselsnummer = "12029240045",
                    skjæringstidspunkt = LocalDate.of(2026, 2, 1),
                    saksbehandlerIdent = "Z999999",
                    handlingId = handlingId,
                    handlingType = "MANUELT_VURDERT_OPPTJENING",
                    kreverTotrinnsvurdering = true,
                    tidspunkt = Instant.parse("2026-02-10T12:00:00Z"),
                ),
            )

        assertEquals("saksbehandlerhandling", melding.path("@event_name").asString())
        assertEquals("12029240045", melding.path("fødselsnummer").asString())
        assertEquals("2026-02-01", melding.path("skjæringstidspunkt").asString())
        assertEquals("Z999999", melding.path("saksbehandlerIdent").asString())
        assertEquals(handlingId.toString(), melding.path("handlingId").asString())
        assertEquals("MANUELT_VURDERT_OPPTJENING", melding.path("handlingType").asString())
        assertTrue(melding.path("kreverTotrinnsvurdering").asBoolean())
        assertEquals("2026-02-10T12:00:00Z", melding.path("tidspunkt").asString())
    }

    @Test
    fun `endret_opptjeningsvurdering har feltene spleis trenger`() {
        val opptjeningsvurderingId = UUID.randomUUID()

        val melding =
            objectMapper.readTree(
                endretOpptjeningsvurdering(
                    fødselsnummer = "12029240045",
                    skjæringstidspunkt = LocalDate.of(2026, 2, 1),
                    opptjeningsvurderingId = opptjeningsvurderingId,
                    manuellVurdering = false,
                ),
            )

        assertEquals("endret_opptjeningsvurdering", melding.path("@event_name").asString())
        assertEquals("12029240045", melding.path("fødselsnummer").asString())
        assertEquals("2026-02-01", melding.path("skjæringstidspunkt").asString())
        assertEquals(opptjeningsvurderingId.toString(), melding.path("opptjeningsvurderingId").asString())
        assertFalse(melding.path("manuellVurdering").asBoolean())
    }
}
