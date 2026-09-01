package no.nav.helse.sykepenger.vilkarsproving.opprydding_dev

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

object Database {
    private val postgresContainer = PostgreSQLContainer("postgres:18").also { it.start() }

    val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = postgresContainer.jdbcUrl
                username = postgresContainer.username
                password = postgresContainer.password
            },
        )

    private val flyway =
        Flyway
            .configure()
            .dataSource(dataSource)
            .cleanDisabled(false)
            .load()
            .also { it.migrate() }

    fun reset() {
        flyway.clean()
        flyway.migrate()
    }

    fun shutdown() {
        dataSource.close()
    }

    fun countOpptjeningsproving(dataSource: DataSource = this.dataSource) = countRows("opptjeningsproving", dataSource)

    fun countOpptjeningsvurdering(dataSource: DataSource = this.dataSource) = countRows("opptjeningsvurdering", dataSource)

    fun countVilkarsvurdering(dataSource: DataSource = this.dataSource) = countRows("vilkarsvurdering", dataSource)

    private fun countRows(
        tabell: String,
        dataSource: DataSource,
    ): Int =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM $tabell").executeQuery().use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }
}
