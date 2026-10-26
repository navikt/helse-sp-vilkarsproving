package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.opptjeningErOppfylt
import java.time.Instant
import java.time.LocalDate

internal sealed interface Opptjeningsvurdering {
    val id: OpptjeningsvurderingId
    val fødselsnummer: String
    val skjæringstidspunkt: LocalDate
    val kategori: Kategori
    val erOk: Boolean
    val vurdertTidspunkt: Instant

    sealed interface MedVilkårsvurderinger : Opptjeningsvurdering {
        val vilkårsvurderinger: List<Vilkårsvurdering>

        override val vurdertTidspunkt: Instant get() = vilkårsvurderinger.maxOf { it.vurdertTidspunkt }
    }

    data class VurdertISpVilkårsprøving(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        override val vilkårsvurderinger: List<Vilkårsvurdering>,
        override val erOk: Boolean,
    ) : MedVilkårsvurderinger {
        init {
            validerVilkårsvurderinger(id, vilkårsvurderinger)
        }

        internal companion object {
            fun med(
                id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
                fødselsnummer: String,
                skjæringstidspunkt: LocalDate,
                kategori: Kategori,
                vilkårsvurderinger: List<Vilkårsvurdering>,
            ) = VurdertISpVilkårsprøving(
                id = id,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = kategori,
                vilkårsvurderinger = vilkårsvurderinger,
                erOk = vilkårsvurderinger.opptjeningErOppfylt(),
            )
        }
    }

    data class OverførtFraSpleis(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        override val vilkårsvurderinger: List<Vilkårsvurdering>,
        override val erOk: Boolean,
    ) : MedVilkårsvurderinger {
        init {
            validerVilkårsvurderinger(id, vilkårsvurderinger)
        }
    }

    data class OverførtFraInfotrygd(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        override val erOk: Boolean,
        override val vurdertTidspunkt: Instant,
    ) : Opptjeningsvurdering

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

        /**
         * Saksbehandlerens samlede vurdering av § 8-2 erstatter alle tidligere vilkårsvurderinger i [forrigeVurdering],
         * slik at det ikke blir liggende igjen gamle vurderinger av punktum som ikke lenger er aktuelle.
         */
        fun avSaksbehandler(
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            forrigeVurdering: Opptjeningsvurdering? = null,
        ): VurdertISpVilkårsprøving =
            VurdertISpVilkårsprøving.med(
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = forrigeVurdering?.kategori ?: Kategori.Arbeidstaker,
                vilkårsvurderinger = vilkårsvurderinger,
            )

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
        ) = OverførtFraSpleis(
            id = id,
            fødselsnummer = fødselsnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            kategori = vilkårsvurderinger.kategori(),
            vilkårsvurderinger = vilkårsvurderinger,
            erOk = vilkårsvurderinger.opptjeningErOppfylt(),
        )

        fun fraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            erOk: Boolean,
            kategori: Kategori = vilkårsvurderinger.kategori(),
        ) = VurdertISpVilkårsprøving(id, fødselsnummer, skjæringstidspunkt, kategori, vilkårsvurderinger, erOk)

        fun spleisFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            erOk: Boolean,
            kategori: Kategori,
        ) = OverførtFraSpleis(id, fødselsnummer, skjæringstidspunkt, kategori, vilkårsvurderinger, erOk)

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
) {
    require(vilkårsvurderinger.isNotEmpty()) { "Opptjeningsvurdering $id må ha minst én vilkårsvurdering" }
}

private fun List<Vilkårsvurdering>.kategori(): Kategori =
    firstNotNullOfOrNull { vurdering ->
        when (val kilde = vurdering.kilde) {
            is Vurderingskilde.Automatisk -> kilde.grunnlag.kategori
            is Vurderingskilde.OverførtFraSpleis -> kilde.grunnlag.kategori
            is Vurderingskilde.Saksbehandler -> null
        }
    } ?: Kategori.Arbeidstaker
