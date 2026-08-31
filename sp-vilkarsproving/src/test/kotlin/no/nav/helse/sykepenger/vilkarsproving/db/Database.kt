package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.speil.backend.app.testfixtures.TestDatabase
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider
import java.sql.ResultSet

/**
 * Vilkårsprøvings appspesifikke oppsett rundt den delte testdatabase-fixturen.
 */
internal object Database {
    private val database = TestDatabase.start(postgresImage = "postgres:18").also { it.migrer() }

    val transaksjonProvider = PostgresTransaksjonProvider(database.dataSource)

    fun tøm() = database.tøm("vilkarsvurdering", "kravproving", "kravvurdering")

    fun antallRader(tabell: String) = database.antallRader(tabell)

    /**
     * Dumper innholdet i de gitte tabellene til stdout, som enkle tabeller — nyttig for å følge med på
     * hvordan datastrukturene endrer seg gjennom en test, f.eks. mellom hvert steg i en e2e-test.
     */
    fun dump(
        vararg tabeller: String,
        overskrift: String? = null,
    ) {
        database.dataSource.connection.use { connection ->
            overskrift?.let { println("\n===== $it =====") }
            for (tabell in tabeller) {
                println("--- $tabell ---")
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT * FROM $tabell ORDER BY løpenummer").use { rs ->
                        println(rs.tilTekst())
                    }
                }
            }
        }
    }

    private fun ResultSet.tilTekst(): String {
        val kolonner = (1..metaData.columnCount).map { metaData.getColumnLabel(it) }
        val rader = mutableListOf<List<String>>()
        while (next()) {
            rader.add(kolonner.map { kolonne -> getObject(kolonne)?.toString() ?: "NULL" })
        }
        if (rader.isEmpty()) return "(ingen rader)"
        val bredder = kolonner.indices.map { i -> (listOf(kolonner[i]) + rader.map { it[i] }).maxOf { it.length } }

        fun rad(felter: List<String>) = felter.mapIndexed { i, felt -> felt.padEnd(bredder[i]) }.joinToString(" | ")
        return buildString {
            appendLine(rad(kolonner))
            appendLine(bredder.joinToString("-+-") { "-".repeat(it) })
            rader.forEach { appendLine(rad(it)) }
        }.trimEnd()
    }
}
