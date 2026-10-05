package no.nav.helse.sykepenger.vilkarsproving.rammeverk.auditlogg

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.NavIdent
import org.slf4j.LoggerFactory
import java.time.Instant

/** Utfallet av et personoppslag/tilgangssjekk, brukt i CEF-loggformatet (`flexString1`). */
enum class AuditloggUtfall {
    Permit,
    Deny,
}

class Auditlogger(
    private val appNavn: String,
    private val meterRegistry: MeterRegistry = Metrics.globalRegistry,
) {
    // CEF krever saksbehandlerident i meldingen; denne loggeren rutes bare til audit.nais.
    private val auditLogg = LoggerFactory.getLogger("auditLogger")

    fun loggPersonoppslag(
        saksbehandler: NavIdent,
        utfall: AuditloggUtfall,
        begrunnelse: String? = null,
    ) {
        val cef =
            buildString {
                append("CEF:0|Vedtaksløsning for sykepenger|$appNavn|1.0|audit:access|Sporingslogg|INFO|")
                append("end=${Instant.now().toEpochMilli()} ")
                append("suid=${saksbehandler.value} ")
                append("flexString1Label=Decision flexString1=${utfall.name}")
                if (begrunnelse != null) {
                    append(" msg=$begrunnelse")
                }
            }
        auditLogg.info(cef)

        meterRegistry
            .counter("auditlog_total", "utfall", utfall.name.lowercase())
            .increment()
    }
}
