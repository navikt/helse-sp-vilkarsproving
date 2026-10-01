package no.nav.helse.sykepenger.vilkarsproving.rammeverk.auditlogg

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.NavIdent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class AuditloggerTest {
    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var auditLogger: Logger

    @BeforeEach
    fun setUp() {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        auditLogger = loggerContext.getLogger("auditLogger")
        appender =
            ListAppender<ILoggingEvent>().apply {
                context = loggerContext
                start()
            }
        auditLogger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        auditLogger.detachAppender(appender)
    }

    @Test
    fun `Permit-utfall logges med flexString1Label=Permit`() {
        val registry = SimpleMeterRegistry()
        val sut = Auditlogger(appNavn = "sp-vilkarsproving", meterRegistry = registry)

        sut.loggPersonoppslag(NavIdent("Z999999"), AuditloggUtfall.Permit)

        val melding = appender.list.single().formattedMessage
        assertTrue(melding.contains("flexString1=Permit"), "forventet flexString1=Permit i: $melding")
    }

    @Test
    fun `Deny-utfall logges med flexString1Label=Deny`() {
        val registry = SimpleMeterRegistry()
        val sut = Auditlogger(appNavn = "sp-vilkarsproving", meterRegistry = registry)

        sut.loggPersonoppslag(NavIdent("Z999999"), AuditloggUtfall.Deny, begrunnelse = "manglende tilgang")

        val melding = appender.list.single().formattedMessage
        assertTrue(melding.contains("flexString1=Deny"), "forventet flexString1=Deny i: $melding")
    }

    @Test
    fun `saksbehandlers NAVident havner i suid-felt, ikke i klartekst-melding`() {
        val registry = SimpleMeterRegistry()
        val sut = Auditlogger(appNavn = "sp-vilkarsproving", meterRegistry = registry)

        sut.loggPersonoppslag(NavIdent("Z123456"), AuditloggUtfall.Permit)

        val melding = appender.list.single().formattedMessage
        assertTrue(melding.contains("suid=Z123456"), "forventet suid=Z123456 i: $melding")
        // CEF-meldingen skal aldri inneholde et fødselsnummer (11 sammenhengende siffer)
        assertFalse(
            Regex("(?<!\\d)\\d{11}(?!\\d)").containsMatchIn(melding),
            "meldingen inneholdt noe som ligner et fødselsnummer: $melding",
        )
    }

    @Test
    fun `auditlog_total-telleren oekes med riktig utfall-tag`() {
        val registry = SimpleMeterRegistry()
        val sut = Auditlogger(appNavn = "sp-vilkarsproving", meterRegistry = registry)

        sut.loggPersonoppslag(NavIdent("Z999999"), AuditloggUtfall.Deny)

        val teller = registry.find("auditlog_total").tag("utfall", "deny").counter()
        assertEquals(1.0, teller?.count())
    }
}
