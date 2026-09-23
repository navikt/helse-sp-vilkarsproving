package no.nav.helse.sykepenger.vilkarsproving.infra.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.sykepenger.vilkarsproving.application.Outbox
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvoluttId
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import org.intellij.lang.annotations.Language
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val objectMapper = jacksonObjectMapper()

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OutboxMeldingDto.OpptjeningsvurderingEndret::class, name = "OPPTJENINGSVURDERING_ENDRET"),
)
private sealed interface OutboxMeldingDto {
    data class OpptjeningsvurderingEndret(
        val skjæringstidspunkt: LocalDate,
        val opptjeningsvurderingId: UUID,
        val manuellVurdering: Boolean,
    ) : OutboxMeldingDto
}

private fun OutboxMelding.tilDto(): OutboxMeldingDto =
    when (this) {
        is OutboxMelding.OpptjeningsvurderingEndret ->
            OutboxMeldingDto.OpptjeningsvurderingEndret(
                skjæringstidspunkt = skjæringstidspunkt,
                opptjeningsvurderingId = opptjeningsvurderingId,
                manuellVurdering = manuellVurdering,
            )
    }

private fun OutboxMeldingDto.tilOutboxMelding(): OutboxMelding =
    when (this) {
        is OutboxMeldingDto.OpptjeningsvurderingEndret ->
            OutboxMelding.OpptjeningsvurderingEndret(
                skjæringstidspunkt = skjæringstidspunkt,
                opptjeningsvurderingId = opptjeningsvurderingId,
                manuellVurdering = manuellVurdering,
            )
    }

internal class PostgresOutbox(
    private val session: Session,
) : Outbox {
    override fun leggTil(konvolutt: OutboxKonvolutt) {
        @Language("PostgreSQL")
        val sql = """
            insert into outbox (id, melding, fodselsnummer)
            values (:id, cast(:melding as jsonb), :fodselsnummer)
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to konvolutt.id.value,
                    "melding" to objectMapper.writeValueAsString(konvolutt.melding.tilDto()),
                    "fodselsnummer" to konvolutt.identitetsnummer.value,
                ),
            ).asUpdate,
        )
    }

    override fun hentUpubliserte(maksAntall: Int): List<OutboxKonvolutt> {
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

    override fun markerSomSendt(id: OutboxKonvoluttId) {
        @Language("PostgreSQL")
        val sql = """
            update outbox set publisert_tidspunkt = :now where id = :id
        """
        session.run(queryOf(sql, mapOf("id" to id.value, "now" to Instant.now())).asUpdate)
    }

    private fun tilUtgåendeLøsning(row: Row) =
        OutboxKonvolutt(
            id = OutboxKonvoluttId(row.uuid("id")),
            melding = objectMapper.readValue<OutboxMeldingDto>(row.string("melding")).tilOutboxMelding(),
            identitetsnummer = Identitetsnummer(row.string("fodselsnummer")),
        )
}
