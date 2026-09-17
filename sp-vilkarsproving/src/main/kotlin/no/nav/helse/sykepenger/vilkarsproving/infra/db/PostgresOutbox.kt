package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.Outbox
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMeldingId
import no.nav.helse.sykepenger.vilkarsproving.application.UtgåendeLøsning
import org.intellij.lang.annotations.Language

internal class PostgresOutbox(
    private val session: Session,
) : Outbox {
    override fun leggTil(melding: UtgåendeLøsning) {
        @Language("PostgreSQL")
        val sql = """
            insert into outbox (id, melding, fodselsnummer)
            values (:id, cast(:melding as jsonb), :fodselsnummer)
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to melding.id.value,
                    "melding" to melding.meldingJson,
                    "fodselsnummer" to melding.fødselsnummer,
                ),
            ).asUpdate,
        )
    }

    override fun hentUpubliserte(maksAntall: Int): List<UtgåendeLøsning> {
        @Language("PostgreSQL")
        val sql = """
            select id, melding, fodselsnummer
            from outbox
            where publisert_tidspunkt is null
            order by opprettet
            limit :maksAntall
            for update skip locked
        """
        return session.run(
            queryOf(sql, mapOf("maksAntall" to maksAntall)).map(::tilUtgåendeLøsning).asList,
        )
    }

    override fun markerSomPublisert(id: OutboxMeldingId) {
        @Language("PostgreSQL")
        val sql = """
            update outbox set publisert_tidspunkt = now() where id = :id
        """
        session.run(queryOf(sql, mapOf("id" to id.value)).asUpdate)
    }

    private fun tilUtgåendeLøsning(row: Row) =
        UtgåendeLøsning(
            id = OutboxMeldingId(row.uuid("id")),
            meldingJson = row.string("melding"),
            fødselsnummer = row.string("fodselsnummer"),
        )
}
