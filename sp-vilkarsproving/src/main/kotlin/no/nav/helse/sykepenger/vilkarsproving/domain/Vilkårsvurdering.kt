package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

internal data class Vilkårsvurdering(
    val id: VilkårsvurderingId,
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val vurdertTidspunkt: Instant?,
    val kilde: Vurderingskilde,
    val lovreferanse: Lovreferanse?,
) {
    private fun erOppfylt() = utfall == Utfall.Oppfylt

    companion object {
        fun List<Vilkårsvurdering>.finn(vilkårskode: Vilkårskode): Vilkårsvurdering? = this.find { it.vilkårskode == vilkårskode }

        fun List<Vilkårsvurdering>.avgjørendeVilkårsvurdering(): Vilkårsvurdering? {
            val hovedregel = this.finn(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER) ?: return null
            if (hovedregel.erOppfylt()) return hovedregel
            return null
        }

        internal fun automatisk(
            opptjeningsprøvingId: OpptjeningsprøvingId,
            vilkårsutfall: Vilkårsutfall,
            grunnlag: Opptjeningsgrunnlag,
            versjonAvKildekode: String,
            vurdertTidspunkt: Instant,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårsutfall.vilkårskode,
            utfall = vilkårsutfall.utfall,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = Vurderingskilde.Automatisk(opptjeningsprøvingId, grunnlag, vilkårsutfall.utledetFakta, versjonAvKildekode),
            lovreferanse = vilkårsutfall.lovreferanse,
        )

        fun avSaksbehandler(
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            saksbehandlerIdent: String,
            fritekstbegrunnelse: String,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = utfall,
            vurdertTidspunkt = Instant.now(),
            kilde = Vurderingskilde.Saksbehandler(saksbehandlerIdent, fritekstbegrunnelse),
            lovreferanse = null,
        )

        fun overførtFraSpleis(
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            grunnlag: Opptjeningsgrunnlag,
            utledetFakta: UtledetFakta,
            vurdertTidspunkt: Instant? = null,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = utfall,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = Vurderingskilde.OverførtFraSpleis(grunnlag, utledetFakta),
            lovreferanse = null,
        )

        fun fraLagring(
            id: VilkårsvurderingId,
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            vurdertTidspunkt: Instant?,
            kilde: Vurderingskilde,
            lovreferanse: Lovreferanse?,
        ) = Vilkårsvurdering(id, vilkårskode, utfall, vurdertTidspunkt, kilde, lovreferanse)
    }
}
