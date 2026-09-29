package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.avgjørendeVilkårsvurdering
import java.time.Instant
import java.time.LocalDate

internal sealed interface Opptjeningsvurdering {
    val id: OpptjeningsvurderingId
    val fødselsnummer: String
    val skjæringstidspunkt: LocalDate
    val kategori: Kategori
    val erOk: Boolean

    fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpVilkårsprøving

    sealed interface MedVilkårsvurderinger : Opptjeningsvurdering {
        val vilkårsvurderinger: List<Vilkårsvurdering>
        val avgjørendeVilkårsvurdering: Vilkårsvurdering?

        val avgjørendeVilkårskode: Vilkårskode? get() = avgjørendeVilkårsvurdering?.vilkårskode

        override fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpVilkårsprøving =
            VurdertISpVilkårsprøving.med(
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = kategori,
                vilkårsvurderinger = vilkårsvurderinger.filter { it.vilkårskode != vilkårsvurdering.vilkårskode } + vilkårsvurdering,
            )
    }

    data class VurdertISpVilkårsprøving(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        override val vilkårsvurderinger: List<Vilkårsvurdering>,
        override val avgjørendeVilkårsvurdering: Vilkårsvurdering?,
        override val erOk: Boolean,
    ) : MedVilkårsvurderinger {
        init {
            validerVilkårsvurderinger(id, vilkårsvurderinger, avgjørendeVilkårsvurdering)
        }

        internal companion object {
            fun ny(
                fødselsnummer: String,
                skjæringstidspunkt: LocalDate,
                vilkårsvurdering: Vilkårsvurdering,
            ): VurdertISpVilkårsprøving =
                med(
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    kategori = Kategori.Arbeidstaker,
                    vilkårsvurderinger = listOf(vilkårsvurdering),
                )

            fun med(
                id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
                fødselsnummer: String,
                skjæringstidspunkt: LocalDate,
                kategori: Kategori,
                vilkårsvurderinger: List<Vilkårsvurdering>,
            ): VurdertISpVilkårsprøving {
                val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
                return VurdertISpVilkårsprøving(
                    id = id,
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    kategori = kategori,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avgjørendeVilkårsvurdering = avgjørendeVilkårsvurdering,
                    erOk = avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt,
                )
            }
        }
    }

    data class OverførtFraSpleis(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        override val vilkårsvurderinger: List<Vilkårsvurdering>,
        override val avgjørendeVilkårsvurdering: Vilkårsvurdering?,
        override val erOk: Boolean,
    ) : MedVilkårsvurderinger {
        init {
            validerVilkårsvurderinger(id, vilkårsvurderinger, avgjørendeVilkårsvurdering)
        }
    }

    data class OverførtFraInfotrygd(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        override val erOk: Boolean,
        val vurdertTidspunkt: Instant,
    ) : Opptjeningsvurdering {
        override fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpVilkårsprøving =
            VurdertISpVilkårsprøving.med(
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = kategori,
                vilkårsvurderinger = listOf(vilkårsvurdering),
            )
    }

    companion object {
        fun automatisk(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            opptjeningsprøvingId: OpptjeningsprøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            grunnlag: Opptjeningsgrunnlag,
            versjonAvKode: String = "test",
        ): VurdertISpVilkårsprøving {
            val resultat = grunnlag.regel.vurder(skjæringstidspunkt, grunnlag)
            val vilkårsvurderinger =
                resultat.vilkårsutfall.map { utfall ->
                    Vilkårsvurdering.automatisk(opptjeningsprøvingId, utfall, grunnlag, versjonAvKode, Instant.now())
                }
            return VurdertISpVilkårsprøving.med(id, fødselsnummer, skjæringstidspunkt, grunnlag.kategori, vilkårsvurderinger)
        }

        fun avSaksbehandler(
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurdering: Vilkårsvurdering,
            forrigeVurdering: Opptjeningsvurdering? = null,
        ): VurdertISpVilkårsprøving {
            if (forrigeVurdering == null) return VurdertISpVilkårsprøving.ny(fødselsnummer, skjæringstidspunkt, vilkårsvurdering)
            return forrigeVurdering.prøvPåNyttMed(vilkårsvurdering)
        }

        fun fraInfotrygd(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
            vurdertTidspunkt: Instant,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, Kategori.Arbeidstaker, erOk, vurdertTidspunkt)

        fun overførtFraSpleis(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
        ): OverførtFraSpleis {
            val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
            return OverførtFraSpleis(
                id = id,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = vilkårsvurderinger.kategori(),
                vilkårsvurderinger = vilkårsvurderinger,
                avgjørendeVilkårsvurdering = avgjørendeVilkårsvurdering,
                erOk = avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt,
            )
        }

        fun fraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            avgjørendeVilkårsvurdering: Vilkårsvurdering?,
            erOk: Boolean,
            kategori: Kategori = vilkårsvurderinger.kategori(),
        ) = VurdertISpVilkårsprøving(id, fødselsnummer, skjæringstidspunkt, kategori, vilkårsvurderinger, avgjørendeVilkårsvurdering, erOk)

        fun spleisFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            avgjørendeVilkårsvurdering: Vilkårsvurdering?,
            erOk: Boolean,
            kategori: Kategori,
        ) = OverførtFraSpleis(id, fødselsnummer, skjæringstidspunkt, kategori, vilkårsvurderinger, avgjørendeVilkårsvurdering, erOk)

        fun infotrygdFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
            vurdertTidspunkt: Instant,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, Kategori.Arbeidstaker, erOk, vurdertTidspunkt)
    }
}

private fun validerVilkårsvurderinger(
    id: OpptjeningsvurderingId,
    vilkårsvurderinger: List<Vilkårsvurdering>,
    avgjørendeVilkårsvurdering: Vilkårsvurdering?,
) {
    require(vilkårsvurderinger.isNotEmpty()) { "Opptjeningsvurdering $id må ha minst én vilkårsvurdering" }
    require(avgjørendeVilkårsvurdering == null || vilkårsvurderinger.any { it.id == avgjørendeVilkårsvurdering.id }) {
        "Avgjørende vilkårsvurdering må tilhøre opptjeningsvurdering $id"
    }
}

private fun List<Vilkårsvurdering>.kategori(): Kategori =
    firstNotNullOfOrNull { vurdering ->
        when (val kilde = vurdering.kilde) {
            is Vurderingskilde.Automatisk -> kilde.grunnlag.kategori
            is Vurderingskilde.OverførtFraSpleis -> kilde.grunnlag.kategori
            is Vurderingskilde.Saksbehandler -> null
        }
    } ?: Kategori.Arbeidstaker
