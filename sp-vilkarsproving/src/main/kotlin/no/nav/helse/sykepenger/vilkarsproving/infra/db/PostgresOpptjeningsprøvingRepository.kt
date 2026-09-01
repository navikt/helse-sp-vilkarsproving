package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningsprøvingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Grunnlagsbehov
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.UUID

internal class PostgresOpptjeningsprøvingRepository(
    private val session: Session,
) : OpptjeningsprøvingRepository {
    override fun lagre(prøving: Opptjeningsprøving) {
        val tilstand = prøving.tilstand.tilLagring()

        @Language("PostgreSQL")
        val sql = """
            insert into opptjeningsproving (id, fødselsnummer, skjæringstidspunkt, startet, tilstand, utestående_behov, opptjeningsvurdering_id)
            values (:id, :fodselsnummer, :skjaeringstidspunkt, :startet, :tilstand, :behov, :opptjeningsvurderingId)
            on conflict (id) do update
            set tilstand = excluded.tilstand,
                utestående_behov = excluded.utestående_behov,
                opptjeningsvurdering_id = excluded.opptjeningsvurdering_id,
                endret = now()
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to prøving.id.value,
                    "fodselsnummer" to prøving.fødselsnummer,
                    "skjaeringstidspunkt" to prøving.skjæringstidspunkt,
                    "startet" to prøving.startet,
                    "tilstand" to tilstand.navn,
                    "behov" to tilstand.behov,
                    "opptjeningsvurderingId" to tilstand.opptjeningsvurderingId,
                ),
            ).asUpdate,
        )
    }

    override fun finnSiste(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Opptjeningsprøving? {
        @Language("PostgreSQL")
        val sql = """
            select id, fødselsnummer, skjæringstidspunkt, startet, tilstand, utestående_behov, opptjeningsvurdering_id
            from opptjeningsproving
            where fødselsnummer = :fodselsnummer and skjæringstidspunkt = :skjaeringstidspunkt
            order by løpenummer desc
            limit 1
        """
        return session.run(
            queryOf(
                sql,
                mapOf(
                    "fodselsnummer" to fødselsnummer,
                    "skjaeringstidspunkt" to skjæringstidspunkt,
                ),
            ).map(::tilPrøving).asSingle,
        )
    }

    private fun tilPrøving(row: Row) =
        Opptjeningsprøving.fraLagring(
            id = OpptjeningsprøvingId(row.uuid("id")),
            fødselsnummer = row.string("fødselsnummer"),
            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
            startet = row.instant("startet"),
            tilstand =
                tilstandFraLagring(
                    navn = row.string("tilstand"),
                    behov = row.stringOrNull("utestående_behov"),
                    opptjeningsvurderingId = row.uuidOrNull("opptjeningsvurdering_id"),
                ),
        )
}

private const val STARTET = "STARTET"
private const val VENTER_PÅ_GRUNNLAG = "VENTER_PÅ_GRUNNLAG"
private const val FULLFØRT = "FULLFØRT"

private class LagretTilstand(
    val navn: String,
    val behov: String? = null,
    val opptjeningsvurderingId: UUID? = null,
)

private fun Opptjeningsprøving.Tilstand.tilLagring() =
    when (this) {
        Opptjeningsprøving.Tilstand.Startet -> LagretTilstand(STARTET)
        is Opptjeningsprøving.Tilstand.VenterPåGrunnlag -> LagretTilstand(VENTER_PÅ_GRUNNLAG, behov = behov.name)
        is Opptjeningsprøving.Tilstand.Fullført -> LagretTilstand(FULLFØRT, opptjeningsvurderingId = opptjeningsvurderingId.value)
    }

private fun tilstandFraLagring(
    navn: String,
    behov: String?,
    opptjeningsvurderingId: UUID?,
): Opptjeningsprøving.Tilstand =
    when (navn) {
        STARTET -> Opptjeningsprøving.Tilstand.Startet
        VENTER_PÅ_GRUNNLAG ->
            Opptjeningsprøving.Tilstand.VenterPåGrunnlag(
                Grunnlagsbehov.valueOf(requireNotNull(behov) { "Prøving i tilstand $navn mangler utestående behov" }),
            )

        FULLFØRT ->
            Opptjeningsprøving.Tilstand.Fullført(
                OpptjeningsvurderingId(requireNotNull(opptjeningsvurderingId) { "Prøving i tilstand $navn mangler opptjeningsvurdering" }),
            )

        else -> error("Kjenner ikke igjen lagret tilstand $navn")
    }
