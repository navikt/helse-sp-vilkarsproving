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
    fun `konverterer V7 og bevarer data ved gjentatt oppstart`() {
        Historikk.entries.forEach { gammelHistorikk ->
            val db = database.opprett()
            gammelHistorikk.opprett(db)
            db.sql(ressurs("/db/eksempeldata.sql"))
            val data = db.data()
            val skjema = db.skjema()

            repeat(2) {
                assertEquals(0, db.flyway().migrate().migrationsExecuted, gammelHistorikk.name)
                db.flyway().validate()
                assertEquals(data, db.data(), gammelHistorikk.name)
                assertEquals(skjema, db.skjema(), gammelHistorikk.name)
            }
            assertEquals(listOf("3|BASELINE|Squash V1-V7"), db.rader("SELECT version || '|' || type || '|' || description FROM flyway_schema_history"))
            assertEquals(0, db.flyway(utenCallback()).migrate().migrationsExecuted)
        }
    }

    @Test
    fun `konverterer V7 uten applikasjonsdata`() {
        Historikk.entries.forEach { gammelHistorikk ->
            val db = database.opprett()
            gammelHistorikk.opprett(db)
            assertEquals(0, db.flyway().migrate().migrationsExecuted)
            assertEquals(
                "BASELINE",
                db
                    .flyway()
                    .info()
                    .current()
                    .type
                    .name(),
                gammelHistorikk.name,
            )
        }
    }

    @Test
    fun `to podder kan konvertere samtidig`() {
        val db = database.opprett()
        Historikk.ETTER_FORRIGE_SQUASH.opprett(db)
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
                "DELETE FROM flyway_schema_history WHERE version = '7'",
                "UPDATE flyway_schema_history SET success = false WHERE version = '7'",
                "UPDATE flyway_schema_history SET checksum = 0 WHERE version = '5'",
                "UPDATE flyway_schema_history SET script = 'V7__ukjent.sql' WHERE version = '7'",
                "UPDATE flyway_schema_history SET type = 'BASELINE' WHERE version = '7'",
                """
                INSERT INTO flyway_schema_history
                    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                VALUES (8, '8', 'ukjent', 'SQL', 'V8__ukjent.sql', 0, current_user, 0, true)
                """.trimIndent(),
            )
        Historikk.entries.forEach { gammelHistorikk ->
            endringer.forEach { endring ->
                val db = database.opprett()
                gammelHistorikk.opprett(db)
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
    }

    @Test
    fun `avviser ufullstendig historikk fra forrige squash`() {
        val db = database.opprett()
        Historikk.ETTER_FORRIGE_SQUASH.opprett(db)
        db.sql("UPDATE flyway_schema_history SET description = 'Squash V1-V7', script = 'Squash V1-V7' WHERE type = 'BASELINE'")
        val historikk = db.historikk()
        val feil = assertThrows(FlywayException::class.java) { db.flyway().migrate() }
        assertTrue(feil.message.orEmpty().contains("Kan ikke squashe"), feil.message)
        assertEquals(historikk, db.historikk())
    }

    @Test
    fun `avviser skjemaendringer uten aa endre historikken`() {
        listOf(
            "ALTER TABLE opptjeningsvurdering ALTER COLUMN kategori DROP NOT NULL",
            "ALTER TABLE vilkarsvurdering ALTER COLUMN lovreferanse DROP NOT NULL",
            "ALTER TABLE opptjeningsvurdering DROP CONSTRAINT fk_opptjeningsvurdering_avgjorende_vilkarsvurdering",
            "DROP INDEX outbox_upublisert",
            "ALTER TABLE opptjeningsproving DROP CONSTRAINT opptjeningsproving_tilstand_er_konsistent",
            "ALTER SEQUENCE opptjeningsproving_løpenummer_seq INCREMENT BY 2",
        ).forEach { endring ->
            val db = database.opprett()
            Historikk.ETTER_FORRIGE_SQUASH.opprett(db)
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
        Historikk.ETTER_FORRIGE_SQUASH.opprett(db)
        db.sql("ALTER TABLE flyway_schema_history ADD CONSTRAINT ingen_ny_baseline CHECK (script <> 'Squash V1-V7')")
        val historikk = db.historikk()
        val feil = assertThrows(FlywayException::class.java) { db.flyway().migrate() }
        assertTrue(feil.message.orEmpty().contains("ingen_ny_baseline"), feil.message)
        assertEquals(historikk, db.historikk())
    }

    @Test
    fun `V4 virker etter baade nyoppretting og konvertering`() {
        val v4 = ressurser.resolve("v4").createDirectory()
        v4.resolve("V4__neste.sql").writeText("CREATE TABLE neste (id INTEGER PRIMARY KEY);")
        listOf(null, *Historikk.entries.toTypedArray()).forEach { gammelHistorikk ->
            val db = database.opprett()
            gammelHistorikk?.opprett(db)
            db.flyway().migrate()
            assertEquals(1, db.flyway(MIGRERINGER, "filesystem:$v4").migrate().migrationsExecuted)
            assertEquals(0, db.flyway(MIGRERINGER, "filesystem:$v4").migrate().migrationsExecuted)
            db.flyway(utenCallback(), "filesystem:$v4").validate()
        }
    }

    @Test
    fun `avviser blanding av gammel og ny historikk`() {
        listOf(
            "(4, '4', 'backfill og obligatorisk lovreferanse', 'SQL', 'V4__backfill_og_obligatorisk_lovreferanse.sql', 635595512, current_user, 0, true)",
            "(4, '3', 'Squash V1-V21', 'BASELINE', 'Squash V1-V21', NULL, current_user, 0, true)",
        ).forEach { rad ->
            val db = database.opprett()
            db.flyway().migrate()
            db.sql(
                """
                INSERT INTO flyway_schema_history
                    (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                VALUES $rad
                """.trimIndent(),
            )
            val historikk = db.historikk()
            assertThrows(FlywayException::class.java) { db.flyway().migrate() }
            assertEquals(historikk, db.historikk())
        }
    }

    private fun utenCallback(): String {
        val mappe = ressurser.resolve("uten-callback-${System.nanoTime()}").createDirectory()
        return Migreringsdatabase.utenCallback(mappe)
    }

    private fun ressurs(navn: String) = requireNotNull(javaClass.getResource(navn)).readText()

    private enum class Historikk {
        ETTER_FORRIGE_SQUASH {
            override fun opprett(db: Migreringsdatabase.Database) {
                db.flyway(LEGACY).migrate()
                db.sql(
                    """
                    DELETE FROM flyway_schema_history WHERE version IN ('1', '2', '3');
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                    VALUES (1, '3', 'Squash V1-V21', 'BASELINE', 'Squash V1-V21', NULL, current_user, 0, true);
                    """.trimIndent(),
                )
            }
        },
        OPPRETTET_ETTER_FORRIGE_SQUASH {
            override fun opprett(db: Migreringsdatabase.Database) {
                db.flyway(LEGACY).migrate()
            }
        },
        ;

        abstract fun opprett(db: Migreringsdatabase.Database)
    }

    companion object {
        private const val LEGACY = "classpath:db/legacy-v1-v7"
        private const val MIGRERINGER = "classpath:db/migration"
    }
}
