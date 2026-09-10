package no.nav.helse.sykepenger.vilkarsproving.opprydding_dev

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

internal class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val dataSource: DataSource,
) : River.PacketListener {
    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "slett_person") }
                validate {
                    it.requireKey("@id", "fødselsnummer")
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fødselsnummer = packet["fødselsnummer"].asString()
        sikkerlogg.info("Sletter person med fødselsnummer: $fødselsnummer")

        sessionOf(dataSource).use { session ->
            session.transaction { tx -> slettPerson(tx, fødselsnummer) }
        }
        context.publish(fødselsnummer, lagPersonSlettet(fødselsnummer))
    }

    // 🔴 Rekkefølgen er ikke tilfeldig: `opptjeningsvurdering_vilkarsvurdering` har fremmednøkler til
    // både `vilkarsvurdering` og `opptjeningsvurdering`, så koblingsradene må slettes før begge disse.
    // En vilkarsvurdering-rad kan i praksis bare være koblet til opptjeningsvurderinger for samme
    // person (den gjenbrukes aldri på tvers av personer), men vi henter likevel ut de koblede
    // vilkarsvurdering-id-ene før koblingene slettes, og sjekker at ingen andre fortsatt peker på dem,
    // i stedet for å anta eksklusivitet. `opptjeningsproving` har derimot ingen fremmednøkkel til
    // `opptjeningsvurdering` (bevisst, jf. kommentarene i migreringene), så den kan slettes uavhengig av
    // rekkefølgen på de tre andre.
    private fun slettPerson(
        tx: TransactionalSession,
        fødselsnummer: String,
    ) {
        val vilkarsvurderingIder = slettKoblingerTilOpptjeningsvurdering(tx, fødselsnummer)
        slettVilkarsvurdering(tx, vilkarsvurderingIder)
        slettOpptjeningsvurdering(tx, fødselsnummer)
        slettOpptjeningsproving(tx, fødselsnummer)
    }

    private fun slettKoblingerTilOpptjeningsvurdering(
        tx: TransactionalSession,
        fødselsnummer: String,
    ): List<UUID> {
        @Language("PostgreSQL")
        val query = """
            DELETE FROM opptjeningsvurdering_vilkarsvurdering
            WHERE opptjeningsvurdering_id IN (
                SELECT id FROM opptjeningsvurdering WHERE fødselsnummer = :fnr
            )
            RETURNING vilkarsvurdering_id
        """
        return tx.run(queryOf(query, mapOf("fnr" to fødselsnummer)).map { it.uuid("vilkarsvurdering_id") }.asList)
    }

    private fun slettVilkarsvurdering(
        tx: TransactionalSession,
        vilkarsvurderingIder: List<UUID>,
    ) {
        if (vilkarsvurderingIder.isEmpty()) return
        @Language("PostgreSQL")
        val query = """
            DELETE FROM vilkarsvurdering
            WHERE id = ANY (:ider)
            AND NOT EXISTS (
                SELECT 1 FROM opptjeningsvurdering_vilkarsvurdering WHERE vilkarsvurdering_id = vilkarsvurdering.id
            )
        """
        tx.run(
            queryOf(
                query,
                mapOf("ider" to tx.createArrayOf("uuid", vilkarsvurderingIder)),
            ).asUpdate,
        )
    }

    private fun slettOpptjeningsvurdering(
        tx: TransactionalSession,
        fødselsnummer: String,
    ) {
        @Language("PostgreSQL")
        val query = "DELETE FROM opptjeningsvurdering WHERE fødselsnummer = :fnr"
        tx.run(queryOf(query, mapOf("fnr" to fødselsnummer)).asUpdate)
    }

    private fun slettOpptjeningsproving(
        tx: TransactionalSession,
        fødselsnummer: String,
    ) {
        @Language("PostgreSQL")
        val query = "DELETE FROM opptjeningsproving WHERE fødselsnummer = :fnr"
        tx.run(queryOf(query, mapOf("fnr" to fødselsnummer)).asUpdate)
    }

    @Language("JSON")
    private fun lagPersonSlettet(fødselsnummer: String): String =
        """
        {
            "@event_name": "person_slettet",
            "fødselsnummer": "$fødselsnummer"
        }
        """.trimIndent()
}
