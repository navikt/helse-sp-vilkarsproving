package no.nav.helse.sykepenger.vilkarsproving.migreringer

import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID
import kotlin.io.path.writeText

internal class Migreringsdatabase : AutoCloseable {
    private val postgres = PostgreSQLContainer("postgres:18")

    fun start() = postgres.start()

    override fun close() = postgres.close()

    fun opprett(bruker: String = postgres.username): Database {
        val navn = "test_${UUID.randomUUID().toString().replace("-", "")}"
        Database(postgres.jdbcUrl, postgres.username, postgres.password).sql("""CREATE DATABASE $navn OWNER "$bruker" """)
        return Database("jdbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/$navn", bruker, postgres.password)
    }

    fun opprettDevRoller() {
        Database(postgres.jdbcUrl, postgres.username, postgres.password).sql(
            """
            CREATE ROLE "sp-vilkarsproving" LOGIN PASSWORD '${postgres.password}';
            CREATE ROLE "sp-vilkarsproving-opprydding-dev";
            """.trimIndent(),
        )
    }

    internal class Database(
        private val url: String,
        private val bruker: String,
        private val passord: String,
    ) {
        fun flyway(vararg locations: String = arrayOf("classpath:db/migration")): Flyway =
            Flyway
                .configure()
                .dataSource(url, bruker, passord)
                .locations(*locations)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .load()

        fun sql(sql: String) =
            DriverManager.getConnection(url, bruker, passord).use { connection ->
                connection.createStatement().use { statement -> statement.execute(sql) }
            }

        fun rader(sql: String): List<String> =
            DriverManager.getConnection(url, bruker, passord).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rows ->
                        buildList {
                            while (rows.next()) add(rows.getString(1))
                        }
                    }
                }
            }

        fun historikk() = rader("SELECT to_jsonb(h)::text FROM flyway_schema_history h ORDER BY installed_rank")

        fun data() =
            tabeller.associateWith { tabell ->
                rader("SELECT to_jsonb(t)::text FROM $tabell t ORDER BY to_jsonb(t)::text COLLATE \"C\"")
            } + ("sekvens" to rader("SELECT last_value::text || '|' || is_called::text FROM opptjeningsproving_løpenummer_seq"))

        fun skjema() = rader(requireNotNull(javaClass.getResource("/db/skjema.sql")).readText())
    }

    companion object {
        val migreringer = listOf("V1__initielt_skjema.sql", "V2__outbox.sql", "V3__opprydding_dev_tilganger.sql")

        fun utenCallback(mappe: Path): String {
            migreringer.forEach { fil ->
                mappe.resolve(fil).writeText(requireNotNull(javaClass.getResource("/db/migration/$fil")).readText())
            }
            return "filesystem:$mappe"
        }

        private val tabeller =
            listOf("opptjeningsproving", "opptjeningsvurdering", "vilkarsvurdering", "opptjeningsvurdering_vilkarsvurdering", "outbox")
    }
}
