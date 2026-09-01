package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import org.junit.jupiter.api.BeforeEach

internal abstract class DatabaseTest {
    @BeforeEach
    fun tømDatabasen() {
        Database.tøm()
    }

    protected fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T = Database.transaksjonProvider.transaksjon(block)
}
