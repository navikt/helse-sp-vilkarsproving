package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant
import java.time.LocalDate

internal sealed interface Kravvurdering {
    val id: KravvurderingId
    val krav: Krav
    val fødselsnummer: String
    val skjæringstidspunkt: LocalDate
    val utfall: Utfall

    data class VurdertISpeil(
        override val id: KravvurderingId,
        override val krav: Krav,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        val vilkårsvurderinger: List<Vilkårsvurdering>,
    ) : Kravvurdering {
        init {
            require(vilkårsvurderinger.isNotEmpty()) { "Kravvurdering $id må ha minst én vilkårsvurdering" }
            vilkårsvurderinger.forEach { ledd ->
                check(ledd.vilkårskode.krav == krav) {
                    "Vilkårskode ${ledd.vilkårskode} hører ikke til kravet $krav i kravvurdering $id"
                }
            }
        }

        override val utfall: Utfall get() = vilkårsvurderinger.last().utfall

        val avgjørendeVilkårskode: Vilkårskode get() = vilkårsvurderinger.last().vilkårskode
    }

    data class OverførtFraInfotrygd(
        override val id: KravvurderingId,
        override val krav: Krav,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val utfall: Utfall,
    ) : Kravvurdering

    companion object {
        fun automatisk(
            id: KravvurderingId = KravvurderingId.ny(),
            prøvingId: PrøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            grunnlag: Vilkårsgrunnlag,
            vurdertTidspunkt: Instant,
        ): VurdertISpeil {
            val regel = grunnlag.krav.regel
            val resultat = regel.vurder(skjæringstidspunkt, grunnlag)
            val vilkårsvurderinger =
                resultat.sti.map { ledd ->
                    Vilkårsvurdering.automatisk(prøvingId, ledd, grunnlag, regel.versjon, vurdertTidspunkt)
                }
            return VurdertISpeil(id, grunnlag.krav, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger)
        }

        fun avSaksbehandler(
            id: KravvurderingId = KravvurderingId.ny(),
            krav: Krav,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, krav, fødselsnummer, skjæringstidspunkt, sti)

        fun fraInfotrygd(
            id: KravvurderingId = KravvurderingId.ny(),
            krav: Krav,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            utfall: Utfall,
        ) = OverførtFraInfotrygd(id, krav, fødselsnummer, skjæringstidspunkt, utfall)

        fun fraLagring(
            id: KravvurderingId,
            krav: Krav,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, krav, fødselsnummer, skjæringstidspunkt, sti)

        fun infotrygdFraLagring(
            id: KravvurderingId,
            krav: Krav,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            utfall: Utfall,
        ) = OverførtFraInfotrygd(id, krav, fødselsnummer, skjæringstidspunkt, utfall)
    }
}
