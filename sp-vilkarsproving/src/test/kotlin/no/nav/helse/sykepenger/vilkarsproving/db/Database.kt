package no.nav.helse.sykepenger.vilkarsproving.infra.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Én Postgres-container for hele testkjøringen, med migreringene fra `migreringer`-modulen.
 *
 * Testene kjører mot det samme skjemaet som produksjon — det er poenget: invariantene ligger i
 * skjemaet, og da må testene se dem.
 */
internal object Database {
    private val container =
        PostgreSQLContainer("postgres:18")
            .also { it.start() }

    val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = container.jdbcUrl
                username = container.username
                password = container.password
                maximumPoolSize = 3
            },
        ).also { dataSource ->
            Flyway
                .configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .load()
                .migrate()
        }

    val transaksjonProvider = PostgresTransaksjonProvider(dataSource)

    fun tøm() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table vilkarsvurdering, vilkarsproving restart identity").asExecute)
        }
    }

    /** Leser utenfor transaksjonen, slik at testene kan skille mellom «lagret» og «rullet tilbake». */
    fun <T> les(
        sql: String,
        mapper: (Row) -> T,
    ): List<T> = sessionOf(dataSource).use { session -> session.run(queryOf(sql).map(mapper).asList) }

    fun antallRader(tabell: String) = les("select count(1) as antall from $tabell") { it.int("antall") }.single()
}
