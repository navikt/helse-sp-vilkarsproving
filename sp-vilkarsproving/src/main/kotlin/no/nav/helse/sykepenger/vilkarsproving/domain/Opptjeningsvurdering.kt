package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.avgjørendeVilkårsvurdering
import java.time.Instant
import java.time.LocalDate

internal sealed interface Opptjeningsvurdering {
    val id: OpptjeningsvurderingId
    val fødselsnummer: String
    val skjæringstidspunkt: LocalDate
    val erOk: Boolean

    fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpeil

    data class VurdertISpeil(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
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
        override val erOk: Boolean,
    ) : Opptjeningsvurdering {
        override fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpeil {
            val vilkårsvurderinger = listOf(vilkårsvurdering)
            val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
            return VurdertISpeil(
                id = OpptjeningsvurderingId.ny(),
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
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
                resultat.vilkårsutfall.map { ledd ->
                    Vilkårsvurdering.automatisk(opptjeningsprøvingId, ledd, grunnlag, regel.versjon, Instant.now())
                }
            val avgjørendeVilkårsvurdering = vilkårsvurderinger.avgjørendeVilkårsvurdering()
            return VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger, avgjørendeVilkårsvurdering?.vilkårskode, avgjørendeVilkårsvurdering?.utfall == Utfall.Oppfylt)
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
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, erOk)

        fun fraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
            avgjørendeVilkårskode: Vilkårskode?,
            erOk: Boolean,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger, avgjørendeVilkårskode, erOk)

        fun overførtFraSpleis(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger, vilkårsvurderinger.avgjørendeVilkårsvurdering()?.vilkårskode, vilkårsvurderinger.avgjørendeVilkårsvurdering()?.utfall == Utfall.Oppfylt)

        fun infotrygdFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, erOk)
    }
}
