package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.VilkårsvurderingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Kodeverkkode
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import java.time.LocalDate

internal class PostgresVilkårsvurderingRepository(
    private val session: Session,
) : VilkårsvurderingRepository {
    override fun lagre(vurdering: Vilkårsvurdering) {
        @Language("PostgreSQL")
        val sql = """
            insert into vilkarsvurdering (id, prøving_id, vilkår, fødselsnummer, skjæringstidspunkt, grunnlag, kodeverkkode, kilde, vurdert_tidspunkt)
            values (:id, :provingId, :vilkar, :fodselsnummer, :skjaeringstidspunkt, cast(:grunnlag as jsonb), :kodeverkkode, cast(:kilde as jsonb), :vurdertTidspunkt)
        """
        try {
            session.run(
                queryOf(
                    sql,
                    mapOf(
                        "id" to vurdering.id.value,
                        "provingId" to vurdering.prøvingId.value,
                        "vilkar" to vurdering.vilkår.name,
                        "fodselsnummer" to vurdering.fødselsnummer,
                        "skjaeringstidspunkt" to vurdering.skjæringstidspunkt,
                        "grunnlag" to Grunnlagsjson.tilJson(vurdering.grunnlag),
                        "kodeverkkode" to vurdering.kodeverkkode.name,
                        "kilde" to Kildejson.tilJson(vurdering.kilde),
                        "vurdertTidspunkt" to vurdering.vurdertTidspunkt,
                    ),
                ).asUpdate,
            )
        } catch (e: PSQLException) {
            if (e.sqlState != UNIKHETSBRUDD) throw e
            throw IllegalStateException("Vurdering ${vurdering.id} er allerede lagret. Vurderinger er immutable.", e)
        }
    }

    override fun gjeldende(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Vilkårsvurdering? {
        @Language("PostgreSQL")
        val sql = """
            $SELECT
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
            ).map(::tilVurdering).asSingle,
        )
    }

    override fun finn(
        vilkår: Vilkår,
        vurderingId: VurderingId,
    ): Vilkårsvurdering? {
        @Language("PostgreSQL")
        val sql = """
            $SELECT
            where vilkår = :vilkar and id = :id
        """
        return session.run(
            queryOf(sql, mapOf("vilkar" to vilkår.name, "id" to vurderingId.value)).map(::tilVurdering).asSingle,
        )
    }

    override fun finnAlle(fødselsnummer: String): List<Vilkårsvurdering> {
        @Language("PostgreSQL")
        val sql = """
            $SELECT
            where fødselsnummer = :fodselsnummer
            order by løpenummer
        """
        return session.run(
            queryOf(sql, mapOf("fodselsnummer" to fødselsnummer)).map(::tilVurdering).asList,
        )
    }

    private fun tilVurdering(row: Row): Vilkårsvurdering {
        val vilkår = Vilkår.valueOf(row.string("vilkår"))
        return Vilkårsvurdering.fraLagring(
            id = VurderingId(row.uuid("id")),
            prøvingId = PrøvingId(row.uuid("prøving_id")),
            fødselsnummer = row.string("fødselsnummer"),
            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
            grunnlag = Grunnlagsjson.fraJson(vilkår, row.string("grunnlag")),
            kodeverkkode = Kodeverkkode.valueOf(row.string("kodeverkkode")),
            kilde = Kildejson.fraJson(row.string("kilde")),
            vurdertTidspunkt = row.instant("vurdert_tidspunkt"),
        )
    }

    private companion object {
        const val UNIKHETSBRUDD = "23505"

        @Language("PostgreSQL")
        const val SELECT = """
            select id, prøving_id, vilkår, fødselsnummer, skjæringstidspunkt, grunnlag, kodeverkkode, kilde, vurdert_tidspunkt
            from vilkarsvurdering
        """
    }
}
