package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningsvurderingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VilkårsvurderingId
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import java.time.LocalDate

private const val KRAVKILDE_VURDERT_I_SPEIL = "VURDERT_I_SPEIL"
private const val KRAVKILDE_OVERFOERT_FRA_INFOTRYGD = "OVERFOERT_FRA_INFOTRYGD"

internal class PostgresOpptjeningsvurderingRepository(
    private val session: Session,
) : OpptjeningsvurderingRepository {
    override fun lagre(vurdering: Opptjeningsvurdering) {
        try {
            when (vurdering) {
                is Opptjeningsvurdering.VurdertISpeil -> lagreVurdertISpeil(vurdering)
                is Opptjeningsvurdering.OverførtFraInfotrygd -> lagreInfotrygd(vurdering)
            }
        } catch (e: PSQLException) {
            if (e.sqlState != UNIKHETSBRUDD) throw e
            throw IllegalStateException("Opptjeningsvurdering ${vurdering.id} er allerede lagret. Vurderinger er immutable.", e)
        }
    }

    private fun lagreVurdertISpeil(vurdering: Opptjeningsvurdering.VurdertISpeil) {
        @Language("PostgreSQL")
        val kravvurderingSql = """
            insert into kravvurdering (id, krav, fødselsnummer, skjæringstidspunkt, kravkilde, rett_til_sykepenger)
            values (:id, :krav, :fodselsnummer, :skjaeringstidspunkt, :kravkilde, :rett_til_sykepenger)
        """
        session.run(
            queryOf(
                kravvurderingSql,
                mapOf(
                    "id" to vurdering.id.value,
                    "krav" to Krav.Opptjening.name,
                    "fodselsnummer" to vurdering.fødselsnummer,
                    "skjaeringstidspunkt" to vurdering.skjæringstidspunkt,
                    "kravkilde" to KRAVKILDE_VURDERT_I_SPEIL,
                    "rett_til_sykepenger" to vurdering.girRettTilSykepenger,
                ),
            ).asUpdate,
        )

        @Language("PostgreSQL")
        val vilkårsvurderingSql = """
            insert into vilkarsvurdering (id, kravvurdering_id, vilkårskode, utfall, vurdert_tidspunkt, kilde)
            values (:id, :opptjeningsvurderingId, :vilkarskode, :utfall, :vurdertTidspunkt, cast(:kilde as jsonb))
        """
        vurdering.vilkårsvurderinger.forEach { ledd ->
            session.run(
                queryOf(
                    vilkårsvurderingSql,
                    mapOf(
                        "id" to ledd.id.value,
                        "opptjeningsvurderingId" to vurdering.id.value,
                        "vilkarskode" to ledd.vilkårskode.name,
                        "utfall" to ledd.utfall.name,
                        "vurdertTidspunkt" to ledd.vurdertTidspunkt,
                        "kilde" to Vurderingskildejson.tilJson(ledd.kilde),
                    ),
                ).asUpdate,
            )
        }
    }

    private fun lagreInfotrygd(vurdering: Opptjeningsvurdering.OverførtFraInfotrygd) {
        @Language("PostgreSQL")
        val sql = """
            insert into kravvurdering (id, krav, fødselsnummer, skjæringstidspunkt, kravkilde, rett_til_sykepenger)
            values (:id, :krav, :fodselsnummer, :skjaeringstidspunkt, :kravkilde, :rett_til_sykepenger)
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to vurdering.id.value,
                    "krav" to Krav.Opptjening.name,
                    "fodselsnummer" to vurdering.fødselsnummer,
                    "skjaeringstidspunkt" to vurdering.skjæringstidspunkt,
                    "kravkilde" to KRAVKILDE_OVERFOERT_FRA_INFOTRYGD,
                    "rett_til_sykepenger" to vurdering.girRettTilSykepenger,
                ),
            ).asUpdate,
        )
    }

    override fun gjeldende(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Opptjeningsvurdering? {
        @Language("PostgreSQL")
        val sql = """
            $SELECT_KRAVVURDERING
            where krav = :krav and fødselsnummer = :fodselsnummer and skjæringstidspunkt = :skjaeringstidspunkt
            order by løpenummer desc
            limit 1
        """
        return session
            .run(
                queryOf(
                    sql,
                    mapOf(
                        "krav" to Krav.Opptjening.name,
                        "fodselsnummer" to fødselsnummer,
                        "skjaeringstidspunkt" to skjæringstidspunkt,
                    ),
                ).map(::tilOpptjeningsvurderingRad).asSingle,
            )?.let(::hydrer)
    }

    override fun finn(opptjeningsvurderingId: OpptjeningsvurderingId): Opptjeningsvurdering? {
        @Language("PostgreSQL")
        val sql = """
            $SELECT_KRAVVURDERING
            where krav = :krav and id = :id
        """
        return session
            .run(
                queryOf(
                    sql,
                    mapOf("krav" to Krav.Opptjening.name, "id" to opptjeningsvurderingId.value),
                ).map(::tilOpptjeningsvurderingRad).asSingle,
            )?.let(::hydrer)
    }

    private fun hydrer(rad: OpptjeningsvurderingRad): Opptjeningsvurdering =
        when (rad.kravkilde) {
            KRAVKILDE_OVERFOERT_FRA_INFOTRYGD ->
                Opptjeningsvurdering.infotrygdFraLagring(
                    id = rad.id,
                    fødselsnummer = rad.fødselsnummer,
                    skjæringstidspunkt = rad.skjæringstidspunkt,
                    girRettTilSykepenger = rad.girRettTilSykepenger,
                )

            else ->
                Opptjeningsvurdering.fraLagring(
                    id = rad.id,
                    fødselsnummer = rad.fødselsnummer,
                    skjæringstidspunkt = rad.skjæringstidspunkt,
                    sti = finnSti(rad.id),
                )
        }

    private fun finnSti(opptjeningsvurderingId: OpptjeningsvurderingId): List<Vilkårsvurdering> {
        @Language("PostgreSQL")
        val sql = """
            select id, vilkårskode, utfall, vurdert_tidspunkt, kilde
            from vilkarsvurdering
            where kravvurdering_id = :opptjeningsvurderingId
            order by løpenummer
        """
        return session.run(
            queryOf(sql, mapOf("opptjeningsvurderingId" to opptjeningsvurderingId.value)).map(::tilVilkårsvurdering).asList,
        )
    }

    private fun tilVilkårsvurdering(row: Row) =
        Vilkårsvurdering.fraLagring(
            id = VilkårsvurderingId(row.uuid("id")),
            vilkårskode = Vilkårskode.valueOf(row.string("vilkårskode")),
            utfall = Utfall.valueOf(row.string("utfall")),
            vurdertTidspunkt = row.instantOrNull("vurdert_tidspunkt"),
            kilde = Vurderingskildejson.fraJson(row.string("kilde")),
        )

    private fun tilOpptjeningsvurderingRad(row: Row) =
        OpptjeningsvurderingRad(
            id = OpptjeningsvurderingId(row.uuid("id")),
            krav = Krav.valueOf(row.string("krav")),
            fødselsnummer = row.string("fødselsnummer"),
            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
            kravkilde = row.string("kravkilde"),
            girRettTilSykepenger = row.boolean("rett_til_sykepenger"),
        )

    private data class OpptjeningsvurderingRad(
        val id: OpptjeningsvurderingId,
        val krav: Krav,
        val fødselsnummer: String,
        val skjæringstidspunkt: LocalDate,
        val kravkilde: String,
        val girRettTilSykepenger: Boolean,
    )

    private companion object {
        const val UNIKHETSBRUDD = "23505"

        @Language("PostgreSQL")
        const val SELECT_KRAVVURDERING = """
            select id, krav, fødselsnummer, skjæringstidspunkt, kravkilde, rett_til_sykepenger
            from kravvurdering
        """
    }
}
