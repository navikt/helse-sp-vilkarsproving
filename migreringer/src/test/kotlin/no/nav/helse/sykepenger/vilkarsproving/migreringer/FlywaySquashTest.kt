package no.nav.helse.sykepenger.vilkarsproving.migreringer

import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.writeText

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class FlywaySquashTest {
    private val database = Migreringsdatabase()

    @TempDir
    lateinit var ressurser: Path

    @BeforeAll
    fun start() = database.start()

    @AfterAll
    fun stopp() = database.close()

    @Test
    fun `konverterer V21 og bevarer data ved gjentatt oppstart`() {
        val db = database.opprett()
        db.flyway(LEGACY).migrate()
        db.sql(ressurs("/db/eksempeldata.sql"))
        val data = db.data()
        val skjema = db.skjema()

        repeat(2) {
            assertEquals(0, db.flyway().migrate().migrationsExecuted)
            db.flyway().validate()
            assertEquals(data, db.data())
            assertEquals(skjema, db.skjema())
        }
        assertEquals(listOf("3|BASELINE|Squash V1-V21"), db.rader("SELECT version || '|' || type || '|' || description FROM flyway_schema_history"))
        assertEquals(0, db.flyway(utenCallback()).migrate().migrationsExecuted)
    }

    @Test
    fun `konverterer V21 uten applikasjonsdata`() {
        val db = database.opprett()
        db.flyway(LEGACY).migrate()
        assertEquals(0, db.flyway().migrate().migrationsExecuted)
        assertEquals(
            "BASELINE",
            db
                .flyway()
                .info()
                .current()
                .type
                .name(),
        )
    }

    @Test
    fun `to podder kan konvertere samtidig`() {
        val db = database.opprett()
        db.flyway(LEGACY).migrate()
        val start = CyclicBarrier(2)
        Executors.newFixedThreadPool(2).use { executor ->
            val resultater =
                (1..2).map {
                    executor.submit(
                        Callable {
                            start.await(10, TimeUnit.SECONDS)
                            db.flyway().migrate().migrationsExecuted
                        },
                    )
                }
            resultater.forEach { assertEquals(0, it.get(30, TimeUnit.SECONDS)) }
        }
        assertEquals(listOf("1"), db.rader("SELECT count(*)::text FROM flyway_schema_history"))
    }

    @Test
    fun `avviser endret eller ufullstendig historikk uten aa endre data`() {
        val endringer =
            listOf(
                "DELETE FROM flyway_schema_history WHERE version = '21'",
                "UPDATE flyway_schema_history SET success = false WHERE version = '21'",
                "UPDATE flyway_schema_history SET checksum = 0 WHERE version = '10'",
                "UPDATE flyway_schema_history SET script = 'V21__ukjent.sql' WHERE version = '21'",
                "UPDATE flyway_schema_history SET type = 'BASELINE' WHERE version = '21'",
                """
                INSERT INTO flyway_schema_history
                    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                VALUES (22, '22', 'ukjent', 'SQL', 'V22__ukjent.sql', 0, current_user, 0, true)
                """.trimIndent(),
            )
        endringer.forEach { endring ->
            val db = database.opprett()
            db.flyway(LEGACY).migrate()
            db.sql(ressurs("/db/eksempeldata.sql"))
            db.sql(endring)
            val historikk = db.historikk()
            val data = db.data()
            val feil = assertThrows(FlywayException::class.java) { db.flyway().migrate() }
            assertTrue(feil.message.orEmpty().contains("Kan ikke squashe"), feil.message)
            assertEquals(historikk, db.historikk(), endring)
            assertEquals(data, db.data(), endring)
        }
    }

    @Test
    fun `avviser skjemaendringer uten aa endre historikken`() {
        listOf(
            "ALTER TABLE opptjeningsvurdering ALTER COLUMN kategori DROP NOT NULL",
            "DROP INDEX outbox_upublisert",
            "ALTER TABLE opptjeningsproving DROP CONSTRAINT opptjeningsproving_tilstand_er_konsistent",
            "ALTER SEQUENCE opptjeningsproving_løpenummer_seq INCREMENT BY 2",
        ).forEach { endring ->
            val db = database.opprett()
            db.flyway(LEGACY).migrate()
            db.sql(endring)
            val historikk = db.historikk()
            val feil = assertThrows(FlywayException::class.java) { db.flyway().migrate() }
            assertTrue(feil.message.orEmpty().contains("skjemaet avviker"), feil.message)
            assertEquals(historikk, db.historikk(), endring)
        }
    }

    @Test
    fun `feil etter sletting av historikk ruller tilbake hele konverteringen`() {
        val db = database.opprett()
        db.flyway(LEGACY).migrate()
        db.sql("ALTER TABLE flyway_schema_history ADD CONSTRAINT ingen_baseline CHECK (type <> 'BASELINE')")
        val historikk = db.historikk()
        val feil = assertThrows(FlywayException::class.java) { db.flyway().migrate() }
        assertTrue(feil.message.orEmpty().contains("ingen_baseline"), feil.message)
        assertEquals(historikk, db.historikk())
    }

    @Test
    fun `V4 virker etter baade nyoppretting og konvertering`() {
        val v4 = ressurser.resolve("v4").createDirectory()
        v4.resolve("V4__neste.sql").writeText("CREATE TABLE neste (id INTEGER PRIMARY KEY);")
        listOf(false, true).forEach { legacy ->
            val db = database.opprett()
            if (legacy) db.flyway(LEGACY).migrate()
            db.flyway().migrate()
            assertEquals(1, db.flyway(MIGRERINGER, "filesystem:$v4").migrate().migrationsExecuted)
            assertEquals(0, db.flyway(MIGRERINGER, "filesystem:$v4").migrate().migrationsExecuted)
            db.flyway(utenCallback(), "filesystem:$v4").validate()
        }
    }

    @Test
    fun `avviser blanding av gammel og ny historikk`() {
        val db = database.opprett()
        db.flyway().migrate()
        db.sql(
            """
            INSERT INTO flyway_schema_history
                (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
            VALUES (4, '13', 'outbox', 'SQL', 'V13__outbox.sql', 1967003348, current_user, 0, true)
            """.trimIndent(),
        )
        val historikk = db.historikk()
        assertThrows(FlywayException::class.java) { db.flyway().migrate() }
        assertEquals(historikk, db.historikk())
    }

    private fun utenCallback(): String {
        val mappe = ressurser.resolve("uten-callback-${System.nanoTime()}").createDirectory()
        return Migreringsdatabase.utenCallback(mappe)
    }

    private fun ressurs(navn: String) = requireNotNull(javaClass.getResource(navn)).readText()

    companion object {
        private const val LEGACY = "classpath:db/legacy-v1-v21"
        private const val MIGRERINGER = "classpath:db/migration"
    }
}
