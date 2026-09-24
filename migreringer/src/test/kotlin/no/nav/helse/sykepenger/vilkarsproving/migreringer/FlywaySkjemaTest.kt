package no.nav.helse.sykepenger.vilkarsproving.migreringer

import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class FlywaySkjemaTest {
    private val database = Migreringsdatabase()

    @TempDir
    lateinit var ressurser: Path

    @BeforeAll
    fun start() = database.start()

    @AfterAll
    fun stopp() = database.close()

    @Test
    fun `ny database kjoerer akkurat V1 til V3 med og uten callback`() {
        val utenCallback = Migreringsdatabase.utenCallback(ressurser)
        listOf("classpath:db/migration", utenCallback).forEach { location ->
            val db = database.opprett()
            assertEquals(3, db.flyway(location).migrate().migrationsExecuted)
            db.flyway(location).validate()
            assertEquals(listOf("1|SQL", "2|SQL", "3|SQL"), db.rader("SELECT version || '|' || type FROM flyway_schema_history ORDER BY installed_rank"))
            assertEquals(0, db.flyway(location).migrate().migrationsExecuted)
        }
    }

    @Test
    fun `nytt skjema tilsvarer V21`() {
        val gammel = database.opprett()
        gammel.flyway("classpath:db/legacy-v1-v21").migrate()
        val ny = database.opprett()
        ny.flyway().migrate()
        assertEquals(gammel.skjema(), ny.skjema())
    }

    @Test
    fun `uferdig ny migrering fortsetter normalt`() {
        listOf("1", "2").forEach { target ->
            val db = database.opprett()
            db.flyway().configuration.let { config ->
                org.flywaydb.core.Flyway
                    .configure()
                    .configuration(config)
                    .target(target)
                    .load()
                    .migrate()
            }
            assertEquals(3 - target.toInt(), db.flyway().migrate().migrationsExecuted)
        }
    }

    @Test
    fun `ukjent ikke-tomt skjema blir ikke baselinet`() {
        val db = database.opprett()
        db.sql("CREATE TABLE ukjent (id INTEGER)")
        assertThrows(FlywayException::class.java) { db.flyway().migrate() }
        assertEquals(listOf("ukjent"), db.rader("SELECT tablename FROM pg_tables WHERE schemaname = 'public'"))
    }

    @Test
    fun `vanlig validering oppdager endret ny migrering`() {
        val db = database.opprett()
        db.flyway().migrate()
        db.sql("UPDATE flyway_schema_history SET checksum = 0 WHERE version = '1'")
        assertThrows(FlywayException::class.java) { db.flyway().migrate() }
    }

    @Test
    fun `dev-rollen beholder tilganger og standardrettigheter`() {
        Migreringsdatabase().use { dev ->
            dev.start()
            dev.opprettDevRoller()
            val gammel = dev.opprett("sp-vilkarsproving")
            gammel.flyway("classpath:db/legacy-v1-v21").migrate()
            val tilganger = gammel.rader(tilgangerSql)
            gammel.flyway().migrate()
            assertEquals(tilganger, gammel.rader(tilgangerSql))
            val ny = dev.opprett("sp-vilkarsproving")
            ny.flyway().migrate()
            assertEquals(tilganger, ny.rader(tilgangerSql))
            listOf(gammel, ny).forEach { db ->
                db.sql("CREATE TABLE fremtidig (id BIGSERIAL PRIMARY KEY)")
                assertEquals(
                    listOf("true"),
                    db.rader(
                        """
                        SELECT (
                            has_table_privilege('sp-vilkarsproving-opprydding-dev', 'outbox', 'DELETE')
                            AND has_table_privilege('sp-vilkarsproving-opprydding-dev', 'opptjeningsvurdering_vilkarsvurdering', 'DELETE')
                            AND has_table_privilege('sp-vilkarsproving-opprydding-dev', 'fremtidig', 'DELETE')
                            AND has_sequence_privilege('sp-vilkarsproving-opprydding-dev', 'fremtidig_id_seq', 'USAGE')
                        )::text
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    private val tilgangerSql =
        """
        SELECT jsonb_build_array(c.relname, a.privilege_type, a.is_grantable)::text AS rettighet
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        CROSS JOIN LATERAL aclexplode(c.relacl) a
        JOIN pg_roles r ON r.oid = a.grantee
        WHERE n.nspname = 'public' AND r.rolname = 'sp-vilkarsproving-opprydding-dev'
        UNION ALL
        SELECT jsonb_build_array('standard', d.defaclobjtype, a.privilege_type, a.is_grantable)::text
        FROM pg_default_acl d
        CROSS JOIN LATERAL aclexplode(d.defaclacl) a
        JOIN pg_roles r ON r.oid = a.grantee
        WHERE r.rolname = 'sp-vilkarsproving-opprydding-dev'
        ORDER BY 1
        """.trimIndent()
}
