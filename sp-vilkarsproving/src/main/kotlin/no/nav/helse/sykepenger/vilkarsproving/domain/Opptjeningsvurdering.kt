package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.avgjørendeVilkårskode
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
    ) : Opptjeningsvurdering {
        init {
            require(vilkårsvurderinger.isNotEmpty()) { "Opptjeningsvurdering $id må ha minst én vilkårsvurdering" }
        }

        override val erOk: Boolean get() = vilkårsvurderinger.last().utfall == Utfall.Oppfylt

        override fun prøvPåNyttMed(vilkårsvurdering: Vilkårsvurdering): VurdertISpeil =
            VurdertISpeil(
                id = OpptjeningsvurderingId.ny(),
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsvurderinger =
                    vilkårsvurderinger
                        .filter { it.vilkårskode != vilkårsvurdering.vilkårskode } +
                        vilkårsvurdering,
                avgjørendeVilkårskode = vilkårsvurderinger.avgjørendeVilkårskode(),
            )

        internal companion object {
            fun ny(
                fødselsnummer: String,
                skjæringstidspunkt: LocalDate,
                vilkårsvurdering: Vilkårsvurdering,
            ): VurdertISpeil {
                val vilkårsvurderinger = listOf(vilkårsvurdering)
                return VurdertISpeil(
                    id = OpptjeningsvurderingId.ny(),
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    vilkårsvurderinger = vilkårsvurderinger,
                    avgjørendeVilkårskode = vilkårsvurderinger.avgjørendeVilkårskode(),
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
            return VurdertISpeil(
                id = OpptjeningsvurderingId.ny(),
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsvurderinger = vilkårsvurderinger,
                avgjørendeVilkårskode = vilkårsvurderinger.avgjørendeVilkårskode(),
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
            return VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger, vilkårsvurderinger.avgjørendeVilkårskode())
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
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger, avgjørendeVilkårskode)

        fun overførtFraSpleis(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            vilkårsvurderinger: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger, vilkårsvurderinger.avgjørendeVilkårskode())

        fun infotrygdFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, erOk)
    }
}
