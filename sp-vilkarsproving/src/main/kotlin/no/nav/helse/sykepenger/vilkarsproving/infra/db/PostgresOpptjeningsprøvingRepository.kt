package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningsprøvingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Grunnlagsbehov
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
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
            insert into kravproving (id, krav, fødselsnummer, skjæringstidspunkt, startet, tilstand, utestående_behov, kravvurdering_id)
            values (:id, :krav, :fodselsnummer, :skjaeringstidspunkt, :startet, :tilstand, :behov, :kravvurderingId)
            on conflict (id) do update
            set tilstand = excluded.tilstand,
                utestående_behov = excluded.utestående_behov,
                kravvurdering_id = excluded.kravvurdering_id,
                endret = now()
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to prøving.id.value,
                    "krav" to Krav.Opptjening.name,
                    "fodselsnummer" to prøving.fødselsnummer,
                    "skjaeringstidspunkt" to prøving.skjæringstidspunkt,
                    "startet" to prøving.startet,
                    "tilstand" to tilstand.navn,
                    "behov" to tilstand.behov,
                    "kravvurderingId" to tilstand.kravvurderingId,
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
            select id, fødselsnummer, skjæringstidspunkt, startet, tilstand, utestående_behov, kravvurdering_id
            from kravproving
            where krav = :krav and fødselsnummer = :fodselsnummer and skjæringstidspunkt = :skjaeringstidspunkt
            order by løpenummer desc
            limit 1
        """
        return session.run(
            queryOf(
                sql,
                mapOf(
                    "krav" to Krav.Opptjening.name,
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
                    kravvurderingId = row.uuidOrNull("kravvurdering_id"),
                ),
        )
}

private const val STARTET = "STARTET"
private const val VENTER_PÅ_GRUNNLAG = "VENTER_PÅ_GRUNNLAG"
private const val FULLFØRT = "FULLFØRT"

private class LagretTilstand(
    val navn: String,
    val behov: String? = null,
    val kravvurderingId: UUID? = null,
)

private fun Opptjeningsprøving.Tilstand.tilLagring() =
    when (this) {
        Opptjeningsprøving.Tilstand.Startet -> LagretTilstand(STARTET)
        is Opptjeningsprøving.Tilstand.VenterPåGrunnlag -> LagretTilstand(VENTER_PÅ_GRUNNLAG, behov = behov.name)
        is Opptjeningsprøving.Tilstand.Fullført -> LagretTilstand(FULLFØRT, kravvurderingId = kravvurderingId.value)
    }

private fun tilstandFraLagring(
    navn: String,
    behov: String?,
    kravvurderingId: UUID?,
): Opptjeningsprøving.Tilstand =
    when (navn) {
        STARTET -> Opptjeningsprøving.Tilstand.Startet
        VENTER_PÅ_GRUNNLAG ->
            Opptjeningsprøving.Tilstand.VenterPåGrunnlag(
                Grunnlagsbehov.valueOf(requireNotNull(behov) { "Prøving i tilstand $navn mangler utestående behov" }),
            )

        FULLFØRT ->
            Opptjeningsprøving.Tilstand.Fullført(
                KravvurderingId(requireNotNull(kravvurderingId) { "Prøving i tilstand $navn mangler kravvurdering" }),
            )

        else -> error("Kjenner ikke igjen lagret tilstand $navn")
    }
