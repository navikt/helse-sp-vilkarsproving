package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.VilkårsprøvingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Grunnlagsbehov
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.util.UUID

internal class PostgresVilkårsprøvingRepository(
    private val session: Session,
) : VilkårsprøvingRepository {
    override fun lagre(prøving: Vilkårsprøving) {
        val tilstand = prøving.tilstand.tilLagring()

        @Language("PostgreSQL")
        val sql = """
            insert into vilkarsproving (id, vilkår, fødselsnummer, skjæringstidspunkt, startet, tilstand, utestående_behov, vurdering_id)
            values (:id, :vilkar, :fodselsnummer, :skjaeringstidspunkt, :startet, :tilstand, :behov, :vurderingId)
            on conflict (id) do update
            set tilstand = excluded.tilstand,
                utestående_behov = excluded.utestående_behov,
                vurdering_id = excluded.vurdering_id,
                endret = now()
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to prøving.id.value,
                    "vilkar" to prøving.vilkår.name,
                    "fodselsnummer" to prøving.fødselsnummer,
                    "skjaeringstidspunkt" to prøving.skjæringstidspunkt,
                    "startet" to prøving.startet,
                    "tilstand" to tilstand.navn,
                    "behov" to tilstand.behov,
                    "vurderingId" to tilstand.vurderingId,
                ),
            ).asUpdate,
        )
    }

    override fun finnSiste(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Vilkårsprøving? {
        @Language("PostgreSQL")
        val sql = """
            select id, vilkår, fødselsnummer, skjæringstidspunkt, startet, tilstand, utestående_behov, vurdering_id
            from vilkarsproving
            where vilkår = :vilkar and fødselsnummer = :fodselsnummer and skjæringstidspunkt = :skjaeringstidspunkt
            order by løpenummer desc
            limit 1
        """
        return session.run(
            queryOf(
                sql,
                mapOf(
                    "vilkar" to vilkår.name,
                    "fodselsnummer" to fødselsnummer,
                    "skjaeringstidspunkt" to skjæringstidspunkt,
                ),
            ).map(::tilPrøving).asSingle,
        )
    }

    private fun tilPrøving(row: Row) =
        Vilkårsprøving.fraLagring(
            id = PrøvingId(row.uuid("id")),
            vilkår = Vilkår.valueOf(row.string("vilkår")),
            fødselsnummer = row.string("fødselsnummer"),
            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
            startet = row.instant("startet"),
            tilstand =
                tilstandFraLagring(
                    navn = row.string("tilstand"),
                    behov = row.stringOrNull("utestående_behov"),
                    vurderingId = row.uuidOrNull("vurdering_id"),
                ),
        )
}

private const val STARTET = "STARTET"
private const val VENTER_PÅ_GRUNNLAG = "VENTER_PÅ_GRUNNLAG"
private const val FULLFØRT = "FULLFØRT"

private class LagretTilstand(
    val navn: String,
    val behov: String? = null,
    val vurderingId: UUID? = null,
)

private fun Vilkårsprøving.Tilstand.tilLagring() =
    when (this) {
        Vilkårsprøving.Tilstand.Startet -> LagretTilstand(STARTET)
        is Vilkårsprøving.Tilstand.VenterPåGrunnlag -> LagretTilstand(VENTER_PÅ_GRUNNLAG, behov = behov.name)
        is Vilkårsprøving.Tilstand.Fullført -> LagretTilstand(FULLFØRT, vurderingId = vurderingId.value)
    }

private fun tilstandFraLagring(
    navn: String,
    behov: String?,
    vurderingId: UUID?,
): Vilkårsprøving.Tilstand =
    when (navn) {
        STARTET -> Vilkårsprøving.Tilstand.Startet
        VENTER_PÅ_GRUNNLAG ->
            Vilkårsprøving.Tilstand.VenterPåGrunnlag(
                Grunnlagsbehov.valueOf(requireNotNull(behov) { "Prøving i tilstand $navn mangler utestående behov" }),
            )

        FULLFØRT ->
            Vilkårsprøving.Tilstand.Fullført(
                VurderingId(requireNotNull(vurderingId) { "Prøving i tilstand $navn mangler vurdering" }),
            )

        else -> error("Kjenner ikke igjen lagret tilstand $navn")
    }
