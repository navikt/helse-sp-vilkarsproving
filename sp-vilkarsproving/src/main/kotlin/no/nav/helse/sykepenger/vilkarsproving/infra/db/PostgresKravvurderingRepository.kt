package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.KravvurderingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import java.time.LocalDate

private const val KRAVKILDE_VURDERT_I_SPEIL = "VURDERT_I_SPEIL"
private const val KRAVKILDE_OVERFOERT_FRA_INFOTRYGD = "OVERFOERT_FRA_INFOTRYGD"

internal class PostgresKravvurderingRepository(
    private val session: Session,
) : KravvurderingRepository {
    override fun lagre(vurdering: Kravvurdering) {
        try {
            when (vurdering) {
                is Kravvurdering.VurdertISpeil -> lagreVurdertISpeil(vurdering)
                is Kravvurdering.OverførtFraInfotrygd -> lagreInfotrygd(vurdering)
            }
        } catch (e: PSQLException) {
            if (e.sqlState != UNIKHETSBRUDD) throw e
            throw IllegalStateException("Kravvurdering ${vurdering.id} er allerede lagret. Vurderinger er immutable.", e)
        }
    }

    private fun lagreVurdertISpeil(vurdering: Kravvurdering.VurdertISpeil) {
        @Language("PostgreSQL")
        val kravvurderingSql = """
            insert into kravvurdering (id, krav, fødselsnummer, skjæringstidspunkt, kravkilde, utfall)
            values (:id, :krav, :fodselsnummer, :skjaeringstidspunkt, :kravkilde, null)
        """
        session.run(
            queryOf(
                kravvurderingSql,
                mapOf(
                    "id" to vurdering.id.value,
                    "krav" to vurdering.krav.name,
                    "fodselsnummer" to vurdering.fødselsnummer,
                    "skjaeringstidspunkt" to vurdering.skjæringstidspunkt,
                    "kravkilde" to KRAVKILDE_VURDERT_I_SPEIL,
                ),
            ).asUpdate,
        )

        @Language("PostgreSQL")
        val vilkårsvurderingSql = """
            insert into vilkarsvurdering (id, kravvurdering_id, vilkårskode, utfall, vurdert_tidspunkt, kilde)
            values (:id, :kravvurderingId, :vilkarskode, :utfall, :vurdertTidspunkt, cast(:kilde as jsonb))
        """
        vurdering.vilkårsvurderinger.forEach { ledd ->
            session.run(
                queryOf(
                    vilkårsvurderingSql,
                    mapOf(
                        "id" to ledd.id.value,
                        "kravvurderingId" to vurdering.id.value,
                        "vilkarskode" to ledd.vilkårskode.name,
                        "utfall" to ledd.utfall.name,
                        "vurdertTidspunkt" to ledd.vurdertTidspunkt,
                        "kilde" to Vurderingskildejson.tilJson(ledd.kilde),
                    ),
                ).asUpdate,
            )
        }
    }

    private fun lagreInfotrygd(vurdering: Kravvurdering.OverførtFraInfotrygd) {
        @Language("PostgreSQL")
        val sql = """
            insert into kravvurdering (id, krav, fødselsnummer, skjæringstidspunkt, kravkilde, utfall)
            values (:id, :krav, :fodselsnummer, :skjaeringstidspunkt, :kravkilde, :utfall)
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to vurdering.id.value,
                    "krav" to vurdering.krav.name,
                    "fodselsnummer" to vurdering.fødselsnummer,
                    "skjaeringstidspunkt" to vurdering.skjæringstidspunkt,
                    "kravkilde" to KRAVKILDE_OVERFOERT_FRA_INFOTRYGD,
                    "utfall" to vurdering.utfall.name,
                ),
            ).asUpdate,
        )
    }

    override fun gjeldende(
        krav: Krav,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Kravvurdering? {
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
                        "krav" to krav.name,
                        "fodselsnummer" to fødselsnummer,
                        "skjaeringstidspunkt" to skjæringstidspunkt,
                    ),
                ).map(::tilKravvurderingRad).asSingle,
            )?.let(::hydrer)
    }

    override fun finn(
        krav: Krav,
        kravvurderingId: KravvurderingId,
    ): Kravvurdering? {
        @Language("PostgreSQL")
        val sql = """
            $SELECT_KRAVVURDERING
            where krav = :krav and id = :id
        """
        return session
            .run(
                queryOf(sql, mapOf("krav" to krav.name, "id" to kravvurderingId.value)).map(::tilKravvurderingRad).asSingle,
            )?.let(::hydrer)
    }

    private fun hydrer(rad: KravvurderingRad): Kravvurdering =
        when (rad.kravkilde) {
            KRAVKILDE_OVERFOERT_FRA_INFOTRYGD ->
                Kravvurdering.infotrygdFraLagring(
                    id = rad.id,
                    krav = rad.krav,
                    fødselsnummer = rad.fødselsnummer,
                    skjæringstidspunkt = rad.skjæringstidspunkt,
                    utfall =
                        Utfall.valueOf(
                            requireNotNull(rad.utfall) { "Kravvurdering ${rad.id} overført fra Infotrygd mangler utfall" },
                        ),
                )

            else ->
                Kravvurdering.fraLagring(
                    id = rad.id,
                    krav = rad.krav,
                    fødselsnummer = rad.fødselsnummer,
                    skjæringstidspunkt = rad.skjæringstidspunkt,
                    sti = finnSti(rad.id),
                )
        }

    private fun finnSti(kravvurderingId: KravvurderingId): List<Vilkårsvurdering> {
        @Language("PostgreSQL")
        val sql = """
            select id, vilkårskode, utfall, vurdert_tidspunkt, kilde
            from vilkarsvurdering
            where kravvurdering_id = :kravvurderingId
            order by løpenummer
        """
        return session.run(
            queryOf(sql, mapOf("kravvurderingId" to kravvurderingId.value)).map(::tilVilkårsvurdering).asList,
        )
    }

    private fun tilVilkårsvurdering(row: Row) =
        Vilkårsvurdering.fraLagring(
            id = VurderingId(row.uuid("id")),
            vilkårskode = Vilkårskode.valueOf(row.string("vilkårskode")),
            utfall = Utfall.valueOf(row.string("utfall")),
            vurdertTidspunkt = row.instantOrNull("vurdert_tidspunkt"),
            kilde = Vurderingskildejson.fraJson(row.string("kilde")),
        )

    private fun tilKravvurderingRad(row: Row) =
        KravvurderingRad(
            id = KravvurderingId(row.uuid("id")),
            krav = Krav.valueOf(row.string("krav")),
            fødselsnummer = row.string("fødselsnummer"),
            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
            kravkilde = row.string("kravkilde"),
            utfall = row.stringOrNull("utfall"),
        )

    private data class KravvurderingRad(
        val id: KravvurderingId,
        val krav: Krav,
        val fødselsnummer: String,
        val skjæringstidspunkt: LocalDate,
        val kravkilde: String,
        val utfall: String?,
    )

    private companion object {
        const val UNIKHETSBRUDD = "23505"

        @Language("PostgreSQL")
        const val SELECT_KRAVVURDERING = """
            select id, krav, fødselsnummer, skjæringstidspunkt, kravkilde, utfall
            from kravvurdering
        """
    }
}
