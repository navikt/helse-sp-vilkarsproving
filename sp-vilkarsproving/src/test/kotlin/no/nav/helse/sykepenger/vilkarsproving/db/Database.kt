package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.speil.backend.app.testfixtures.TestDatabase
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider

/**
 * Vilkårsprøvings appspesifikke oppsett rundt den delte testdatabase-fixturen.
 */
internal object Database {
    private val database = TestDatabase.start(postgresImage = "postgres:18").also { it.migrer() }

    val transaksjonProvider = PostgresTransaksjonProvider(database.dataSource)

    fun tøm() = database.tøm("vilkarsvurdering", "kravproving", "kravvurdering")

    fun antallRader(tabell: String) = database.antallRader(tabell)
}
