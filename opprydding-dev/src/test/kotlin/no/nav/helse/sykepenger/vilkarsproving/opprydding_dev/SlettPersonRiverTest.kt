package no.nav.helse.sykepenger.vilkarsproving.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

internal class SlettPersonRiverTest {
    private val rapid =
        TestRapid().apply {
            SlettPersonRiver(this, Database.dataSource)
        }

    @BeforeEach
    fun beforeEach() {
        Database.reset()
        rapid.reset()
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun shutdown() {
            Database.shutdown()
        }
    }

    @Test
    fun `sletter all data for en person`() {
        val fødselsnummer = "01020312345"

        insertOpptjeningsproving(fødselsnummer = fødselsnummer)
        val opptjeningsvurderingId = insertOpptjeningsvurdering(fødselsnummer = fødselsnummer)
        insertVilkarsvurdering(opptjeningsvurderingId = opptjeningsvurderingId)

        assertEquals(1, Database.countOpptjeningsproving())
        assertEquals(1, Database.countOpptjeningsvurdering())
        assertEquals(1, Database.countVilkarsvurdering())

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(0, Database.countOpptjeningsproving())
        assertEquals(0, Database.countOpptjeningsvurdering())
        assertEquals(0, Database.countVilkarsvurdering())
    }

    @Test
    fun `sletter ikke data for annen person`() {
        val fødselsnummer1 = "01020312345"
        val fødselsnummer2 = "02020312345"

        insertOpptjeningsproving(fødselsnummer = fødselsnummer1)
        val opptjeningsvurderingId1 = insertOpptjeningsvurdering(fødselsnummer = fødselsnummer1)
        insertVilkarsvurdering(opptjeningsvurderingId = opptjeningsvurderingId1)

        insertOpptjeningsproving(fødselsnummer = fødselsnummer2)
        val opptjeningsvurderingId2 = insertOpptjeningsvurdering(fødselsnummer = fødselsnummer2)
        insertVilkarsvurdering(opptjeningsvurderingId = opptjeningsvurderingId2)

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer1))

        assertEquals(1, Database.countOpptjeningsproving())
        assertEquals(1, Database.countOpptjeningsvurdering())
        assertEquals(1, Database.countVilkarsvurdering())
    }

    @Test
    fun `publiserer person_slettet etter sletting`() {
        val fødselsnummer = "01020312345"
        insertOpptjeningsproving(fødselsnummer = fødselsnummer)

        rapid.sendTestMessage(slettPersonMelding(fødselsnummer))

        assertEquals(1, rapid.inspektør.size)
        val kvittering = rapid.inspektør.message(0)
        assertEquals("person_slettet", kvittering["@event_name"].asText())
        assertEquals(fødselsnummer, kvittering["fødselsnummer"].asText())
    }

    @Test
    fun `gjør ingenting for person uten data`() {
        rapid.sendTestMessage(slettPersonMelding("99999999999"))

        assertEquals(0, Database.countOpptjeningsproving())
    }

    private fun insertOpptjeningsproving(
        id: UUID = UUID.randomUUID(),
        fødselsnummer: String,
    ) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO opptjeningsproving
                    (id, fødselsnummer, skjæringstidspunkt, startet, tilstand)
                VALUES (?, ?, ?, ?, 'STARTET')
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setString(2, fødselsnummer)
                    stmt.setObject(3, LocalDate.of(2026, 1, 1))
                    stmt.setObject(4, java.sql.Timestamp.from(Instant.now()))
                    stmt.executeUpdate()
                }
        }
    }

    private fun insertOpptjeningsvurdering(
        id: UUID = UUID.randomUUID(),
        fødselsnummer: String,
    ): UUID {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO opptjeningsvurdering
                    (id, fødselsnummer, skjæringstidspunkt, vurderingskilde, rett_til_sykepenger)
                VALUES (?, ?, ?, 'VURDERT_I_SPEIL', true)
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setString(2, fødselsnummer)
                    stmt.setObject(3, LocalDate.of(2026, 1, 1))
                    stmt.executeUpdate()
                }
        }
        return id
    }

    private fun insertVilkarsvurdering(
        id: UUID = UUID.randomUUID(),
        opptjeningsvurderingId: UUID,
    ) {
        Database.dataSource.connection.use { conn ->
            conn
                .prepareStatement(
                    """
                INSERT INTO vilkarsvurdering
                    (id, opptjeningsvurdering_id, vilkårskode, utfall, kilde)
                VALUES (?, ?, 'FIREUKERSVILKAARET', 'OPPFYLT', '{}'::jsonb)
                """,
                ).use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, opptjeningsvurderingId)
                    stmt.executeUpdate()
                }
        }
    }

    private fun slettPersonMelding(fødselsnummer: String) =
        """
        {
            "@event_name": "slett_person",
            "@id": "${UUID.randomUUID()}",
            "fødselsnummer": "$fødselsnummer"
        }
        """.trimIndent()
}
