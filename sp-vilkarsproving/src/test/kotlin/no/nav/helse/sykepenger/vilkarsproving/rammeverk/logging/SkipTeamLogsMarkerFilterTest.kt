package no.nav.helse.sykepenger.vilkarsproving.rammeverk.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.spi.FilterReply
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class SkipTeamLogsMarkerFilterTest {
    @Test
    fun `filteret er registrert på team-logs-appenderen`() {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val tjenestekallLogger = loggerContext.getLogger("tjenestekall") as Logger

        val appender = tjenestekallLogger.iteratorForAppenders().asSequence().toList()
        check(appender.isNotEmpty()) {
            "Fant ingen appendere på 'tjenestekall'-loggeren – sjekk at logback-test.xml/logback.xml er på classpath"
        }

        val filtre = appender.flatMap { it.copyOfAttachedFiltersList }
        val skipFilter = filtre.filterIsInstance<SkipTeamLogsMarkerFilter>()
        check(skipFilter.isNotEmpty()) {
            "SkipTeamLogsMarkerFilter er ikke registrert på 'tjenestekall'-appenderen – " +
                "sjekk at filterklassens fulle navn i logback.xml stemmer med ${SkipTeamLogsMarkerFilter::class.qualifiedName}"
        }
    }

    @Test
    fun `filteret avviser meldinger med SKIP_TEAM_LOGS_MARKER`() {
        val filter = SkipTeamLogsMarkerFilter()
        val event = mockLoggingEvent(withMarker = true)
        assertEquals(FilterReply.DENY, filter.decide(event))
    }

    @Test
    fun `filteret godtar meldinger uten SKIP_TEAM_LOGS_MARKER`() {
        val filter = SkipTeamLogsMarkerFilter()
        val event = mockLoggingEvent(withMarker = false)
        assertEquals(FilterReply.ACCEPT, filter.decide(event))
    }

    private fun mockLoggingEvent(withMarker: Boolean): ILoggingEvent {
        val event = mockk<ILoggingEvent>()
        every { event.markerList } returns if (withMarker) listOf(SKIP_TEAM_LOGS_MARKER) else emptyList()
        return event
    }
}
