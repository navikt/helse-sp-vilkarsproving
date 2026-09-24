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

    fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpeil

    data class VurdertISpeil(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val kategori: Kategori,
        val vilkårsvurderinger: List<Vilkårsvurdering>,
        val avgjørendeVilkårskode: Vilkårskode?,
        override val erOk: Boolean,
    ) : Opptjeningsvurdering {
        init {
            require(vilkårsvurderinger.isNotEmpty()) { "Opptjeningsvurdering $id må ha minst én vilkårsvurdering" }
        }

        override fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpeil {
            val vilkårsvurderinger =
                vilkårsvurderinger
                    .filter { it.vilkårskode != vilkårsvurdering.vilkårskode } +
                    vilkårsvurdering
            val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
            return VurdertISpeil(
                id = OpptjeningsvurderingId.ny(),
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = kategori,
                vilkårsvurderinger = vilkårsvurderinger,
                avgjørendeVilkårskode = avgjørendeVilkårsvurdering?.vilkårskode,
                erOk = avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt,
            )
        }

        internal companion object {
            fun ny(
                fødselsnummer: String,
                skjæringstidspunkt: LocalDate,
                vilkårsvurdering: Vilkårsvurdering,
            ): VurdertISpeil {
                val vilkårsvurderinger = listOf(vilkårsvurdering)
                val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
                return VurdertISpeil(
                    id = OpptjeningsvurderingId.ny(),
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    kategori = Kategori.Arbeidstaker,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avgjørendeVilkårskode = avgjørendeVilkårsvurdering?.vilkårskode,
                    erOk = avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt,
                )
            }
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
        override fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpeil {
            val vilkårsvurderinger = listOf(vilkårsvurdering)
            val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
            return VurdertISpeil(
                id = OpptjeningsvurderingId.ny(),
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = kategori,
                vilkårsvurderinger = vilkårsvurderinger,
                avgjørendeVilkårskode = avgjørendeVilkårsvurdering?.vilkårskode,
                erOk = avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt,
            )
        }
    }

    companion object {
        fun automatisk(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            opptjeningsprøvingId: OpptjeningsprøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            grunnlag: Opptjeningsgrunnlag,
        ): VurdertISpeil {
            val regel = grunnlag.regel
            val resultat = regel.vurder(skjæringstidspunkt, grunnlag)
            val vilkårsvurderinger =
                resultat.vilkårsutfall.map { utfall ->
                    Vilkårsvurdering.automatisk(opptjeningsprøvingId, utfall, grunnlag, regel.versjon, Instant.now())
                }
            val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
            return VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, grunnlag.kategori, vilkårsvurderinger, avgjørendeVilkårsvurdering?.vilkårskode, avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt)
        }

        fun avSaksbehandler(
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurdering: Vilkårsvurdering,
            forrigeVurdering: Opptjeningsvurdering? = null,
        ): VurdertISpeil {
            if (forrigeVurdering == null) return VurdertISpeil.ny(fødselsnummer, skjæringstidspunkt, vilkårsvurdering)
            return forrigeVurdering.prøvPåNyttMed(vilkårsvurdering)
        }

        fun fraInfotrygd(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
            vurdertTidspunkt: Instant = Instant.now(),
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, Kategori.Arbeidstaker, erOk, vurdertTidspunkt)

        fun fraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            avgjørendeVilkårskode: Vilkårskode?,
            erOk: Boolean,
            kategori: Kategori = vilkårsvurderinger.kategori(),
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, kategori, vilkårsvurderinger, avgjørendeVilkårskode, erOk)

        fun overførtFraSpleis(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger.kategori(), vilkårsvurderinger, vilkårsvurderinger.avgjørendeVilkårsvurdering()?.vilkårskode, vilkårsvurderinger.avgjørendeVilkårsvurdering()?.utfall == Utfall.Oppfylt)

        fun infotrygdFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
            vurdertTidspunkt: Instant,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, Kategori.Arbeidstaker, erOk, vurdertTidspunkt)
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
