package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

internal data class Vilkårsvurdering(
    val id: VilkårsvurderingId,
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val vurdertTidspunkt: Instant?,
    val kilde: Vurderingskilde,
) {
    companion object {
        internal fun automatisk(
            opptjeningsprøvingId: OpptjeningsprøvingId,
            vilkårsutfall: Vilkårsutfall,
            grunnlag: Vilkårsgrunnlag,
            versjonAvKildekode: String,
            vurdertTidspunkt: Instant,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårsutfall.vilkårskode,
            utfall = vilkårsutfall.utfall,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = Vurderingskilde.Automatisk(opptjeningsprøvingId, grunnlag, vilkårsutfall.utledetFakta, versjonAvKildekode),
        )

        fun avSaksbehandler(
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            saksbehandlerIdent: String,
            fritekstbegrunnelse: String,
            vurdertTidspunkt: Instant?,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = utfall,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = Vurderingskilde.Saksbehandler(saksbehandlerIdent, fritekstbegrunnelse),
        )

        fun overførtFraSpleis(
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            grunnlag: Vilkårsgrunnlag,
            utledetFakta: UtledetFakta,
            vurdertTidspunkt: Instant? = null,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = utfall,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = Vurderingskilde.OverførtFraSpleis(grunnlag, utledetFakta),
        )

        fun fraLagring(
            id: VilkårsvurderingId,
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            vurdertTidspunkt: Instant?,
            kilde: Vurderingskilde,
        ) = Vilkårsvurdering(id, vilkårskode, utfall, vurdertTidspunkt, kilde)
    }
}
