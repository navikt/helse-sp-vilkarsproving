package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Row
import kotliquery.Session
import kotliquery.queryOf
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningsvurderingRepository
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VilkårsvurderingId
import org.intellij.lang.annotations.Language
import org.postgresql.util.PSQLException
import java.time.LocalDate

private const val VURDERINGSKILDE_VURDERT_I_SPEIL = "VURDERT_I_SPEIL"
private const val VURDERINGSKILDE_OVERFOERT_FRA_INFOTRYGD = "OVERFOERT_FRA_INFOTRYGD"

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

    private fun lagreVurdertISpeil(totalvurdering: Opptjeningsvurdering.VurdertISpeil) {
        val vurdertTidspunkt = totalvurdering.vilkårsvurderinger.mapNotNull { it.vurdertTidspunkt }.maxOrNull()

        @Language("PostgreSQL")
        val opptjeningsvurderingSql = """
            insert into opptjeningsvurdering (
                id, fødselsnummer, skjæringstidspunkt, vurderingskilde, opptjening_ok, avgjørende_vilkårskode, vurdert_tidspunkt
            )
            values (
                :id, :fodselsnummer, :skjaeringstidspunkt, :vurderingskilde, :opptjening_ok, :avgjorendeVilkarskode,
                coalesce(:vurdertTidspunkt, now())
            )
        """
        session.run(
            queryOf(
                opptjeningsvurderingSql,
                mapOf(
                    "id" to totalvurdering.id.value,
                    "fodselsnummer" to totalvurdering.fødselsnummer,
                    "skjaeringstidspunkt" to totalvurdering.skjæringstidspunkt,
                    "vurderingskilde" to VURDERINGSKILDE_VURDERT_I_SPEIL,
                    "opptjening_ok" to totalvurdering.erOk,
                    "avgjorendeVilkarskode" to totalvurdering.avgjørendeVilkårskode?.name,
                    "vurdertTidspunkt" to vurdertTidspunkt,
                ),
            ).asUpdate,
        )

        // En videreført vilkårsvurdering gjenbruker samme id og finnes derfor allerede i tabellen —
        // ON CONFLICT DO NOTHING gjør innsettingen idempotent uten å måtte skille på om vurderingen er ny
        // eller videreført. Innholdet kan uansett ikke ha endret seg: vilkårsvurderinger er immutable.
        @Language("PostgreSQL")
        val vilkårsvurderingSql = """
            insert into vilkarsvurdering (id, vilkårskode, utfall, vurdert_tidspunkt, kilde, lovreferanse, journalpost_id)
            values (:id, :vilkarskode, :utfall, :vurdertTidspunkt, cast(:kilde as jsonb), cast(:lovreferanse as jsonb), cast(:journalpostId as jsonb))
            on conflict (id) do nothing
        """

        @Language("PostgreSQL")
        val lagKoblingSql = """
            insert into opptjeningsvurdering_vilkarsvurdering (opptjeningsvurdering_id, vilkarsvurdering_id)
            values (:opptjeningsvurderingId, :vilkarsvurderingId)
        """
        totalvurdering.vilkårsvurderinger.forEach { enkeltvurdering ->
            session.run(
                queryOf(
                    vilkårsvurderingSql,
                    mapOf(
                        "id" to enkeltvurdering.id.value,
                        "vilkarskode" to enkeltvurdering.vilkårskode.name,
                        "utfall" to enkeltvurdering.utfall.name,
                        "vurdertTidspunkt" to enkeltvurdering.vurdertTidspunkt,
                        "kilde" to Vurderingskildejson.tilJson(enkeltvurdering.kilde),
                        "lovreferanse" to enkeltvurdering.lovreferanse?.let { Lovreferansejson.tilJson(it) },
                        "journalpostId" to JournalpostIdjson.tilJson(enkeltvurdering.journalpostId),
                    ),
                ).asUpdate,
            )

            session.run(
                queryOf(
                    lagKoblingSql,
                    mapOf(
                        "opptjeningsvurderingId" to totalvurdering.id.value,
                        "vilkarsvurderingId" to enkeltvurdering.id.value,
                    ),
                ).asUpdate,
            )
        }
    }

    private fun lagreInfotrygd(totalvurdering: Opptjeningsvurdering.OverførtFraInfotrygd) {
        @Language("PostgreSQL")
        val sql = """
            insert into opptjeningsvurdering (
                id, fødselsnummer, skjæringstidspunkt, vurderingskilde, opptjening_ok, vurdert_tidspunkt
            )
            values (
                :id, :fodselsnummer, :skjaeringstidspunkt, :vurderingskilde, :opptjening_ok, :vurdertTidspunkt
            )
        """
        session.run(
            queryOf(
                sql,
                mapOf(
                    "id" to totalvurdering.id.value,
                    "fodselsnummer" to totalvurdering.fødselsnummer,
                    "skjaeringstidspunkt" to totalvurdering.skjæringstidspunkt,
                    "vurderingskilde" to VURDERINGSKILDE_OVERFOERT_FRA_INFOTRYGD,
                    "opptjening_ok" to totalvurdering.erOk,
                    "vurdertTidspunkt" to totalvurdering.vurdertTidspunkt,
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
            $SELECT_OPPTJENINGSVURDERING
            where fødselsnummer = :fodselsnummer and skjæringstidspunkt = :skjaeringstidspunkt
            order by vurdert_tidspunkt desc, opprettet desc
            limit 1
        """
        return session
            .run(
                queryOf(
                    sql,
                    mapOf(
                        "fodselsnummer" to fødselsnummer,
                        "skjaeringstidspunkt" to skjæringstidspunkt,
                    ),
                ).map(::tilOpptjeningsvurderingRad).asSingle,
            )?.let(::hydrer)
    }

    override fun finn(opptjeningsvurderingId: OpptjeningsvurderingId): Opptjeningsvurdering? {
        @Language("PostgreSQL")
        val sql = """
            $SELECT_OPPTJENINGSVURDERING
            where id = :id
        """
        return session
            .run(
                queryOf(sql, mapOf("id" to opptjeningsvurderingId.value)).map(::tilOpptjeningsvurderingRad).asSingle,
            )?.let(::hydrer)
    }

    private fun hydrer(rad: OpptjeningsvurderingRad): Opptjeningsvurdering =
        when (rad.vurderingskilde) {
            VURDERINGSKILDE_OVERFOERT_FRA_INFOTRYGD ->
                Opptjeningsvurdering.infotrygdFraLagring(
                    id = rad.id,
                    fødselsnummer = rad.fødselsnummer,
                    skjæringstidspunkt = rad.skjæringstidspunkt,
                    erOk = rad.erOk,
                    vurdertTidspunkt = rad.vurdertTidspunkt,
                )

            else ->
                Opptjeningsvurdering.fraLagring(
                    id = rad.id,
                    fødselsnummer = rad.fødselsnummer,
                    skjæringstidspunkt = rad.skjæringstidspunkt,
                    vilkårsvurderinger = finnVilkårsvurderingerFor(rad.id),
                    avgjørendeVilkårskode = rad.avgjørendeVilkårskode,
                    erOk = rad.erOk,
                )
        }

    private fun finnVilkårsvurderingerFor(opptjeningsvurderingId: OpptjeningsvurderingId): List<Vilkårsvurdering> {
        @Language("PostgreSQL")
        val sql = """
            select v.id, v.vilkårskode, v.utfall, v.vurdert_tidspunkt, v.kilde, v.lovreferanse, v.journalpost_id
            from opptjeningsvurdering_vilkarsvurdering ov
            join vilkarsvurdering v on v.id = ov.vilkarsvurdering_id
            where ov.opptjeningsvurdering_id = :opptjeningsvurderingId
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
            lovreferanse = row.stringOrNull("lovreferanse")?.let { Lovreferansejson.fraJson(it) },
            journalpostId = JournalpostIdjson.fraJson(row.string("journalpost_id")),
        )

    private fun tilOpptjeningsvurderingRad(row: Row) =
        OpptjeningsvurderingRad(
            id = OpptjeningsvurderingId(row.uuid("id")),
            fødselsnummer = row.string("fødselsnummer"),
            skjæringstidspunkt = row.localDate("skjæringstidspunkt"),
            vurderingskilde = row.string("vurderingskilde"),
            erOk = row.boolean("opptjening_ok"),
            avgjørendeVilkårskode = row.stringOrNull("avgjørende_vilkårskode")?.let(Vilkårskode::valueOf),
            vurdertTidspunkt = row.instant("vurdert_tidspunkt"),
        )

    private data class OpptjeningsvurderingRad(
        val id: OpptjeningsvurderingId,
        val fødselsnummer: String,
        val skjæringstidspunkt: LocalDate,
        val vurderingskilde: String,
        val erOk: Boolean,
        val avgjørendeVilkårskode: Vilkårskode?,
        val vurdertTidspunkt: java.time.Instant,
    )

    private companion object {
        const val UNIKHETSBRUDD = "23505"

        @Language("PostgreSQL")
        const val SELECT_OPPTJENINGSVURDERING = """
            select id, fødselsnummer, skjæringstidspunkt, vurderingskilde, opptjening_ok, avgjørende_vilkårskode, vurdert_tidspunkt
            from opptjeningsvurdering
        """
    }
}
