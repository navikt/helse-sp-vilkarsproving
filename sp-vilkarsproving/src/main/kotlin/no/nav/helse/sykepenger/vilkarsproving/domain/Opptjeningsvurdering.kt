package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant
import java.time.LocalDate

internal sealed interface Opptjeningsvurdering {
    val id: OpptjeningsvurderingId
    val fødselsnummer: String
    val skjæringstidspunkt: LocalDate
    val girRettTilSykepenger: Boolean

    data class VurdertISpeil(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        val vilkårsvurderinger: List<Vilkårsvurdering>,
    ) : Opptjeningsvurdering {
        init {
            require(vilkårsvurderinger.isNotEmpty()) { "Opptjeningsvurdering $id må ha minst én vilkårsvurdering" }
        }

        override val girRettTilSykepenger: Boolean get() = vilkårsvurderinger.last().utfall == Utfall.Oppfylt

        val avgjørendeVilkårskode: Vilkårskode get() = vilkårsvurderinger.last().vilkårskode
    }

    data class OverførtFraInfotrygd(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val girRettTilSykepenger: Boolean,
    ) : Opptjeningsvurdering

    companion object {
        fun automatisk(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            opptjeningsprøvingId: OpptjeningsprøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            grunnlag: Vilkårsgrunnlag,
            vurdertTidspunkt: Instant,
        ): VurdertISpeil {
            val regel = grunnlag.krav.regel
            val resultat = regel.vurder(skjæringstidspunkt, grunnlag)
            val vilkårsvurderinger =
                resultat.vilkårsutfall.map { ledd ->
                    Vilkårsvurdering.automatisk(opptjeningsprøvingId, ledd, grunnlag, regel.versjon, vurdertTidspunkt)
                }
            return VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger)
        }

        fun avSaksbehandler(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, sti)

        fun fraInfotrygd(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            girRettTilSykepenger: Boolean,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, girRettTilSykepenger)

        fun fraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, sti)

        fun overførtFraSpleis(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, sti)

        fun infotrygdFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            girRettTilSykepenger: Boolean,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, girRettTilSykepenger)
    }
}
