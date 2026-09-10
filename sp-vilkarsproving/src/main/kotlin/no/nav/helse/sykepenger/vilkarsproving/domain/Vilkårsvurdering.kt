package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

internal data class Vilkårsvurdering(
    val id: VilkårsvurderingId,
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val vurdertTidspunkt: Instant?,
    val kilde: Vurderingskilde,
) {
    private fun erOppfylt() = utfall == Utfall.Oppfylt

    companion object {
        fun List<Vilkårsvurdering>.finn(vilkårskode: Vilkårskode): Vilkårsvurdering? = this.find { it.vilkårskode == vilkårskode }

        fun List<Vilkårsvurdering>.avgjørendeVilkårskode(): Vilkårskode? {
            check(this.isNotEmpty()) { "Listen med vilkårsvurderinger kan ikke være tom" }
            val hovedregel = this.finn(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER)
            if (hovedregel != null) {
                if (hovedregel.erOppfylt()) return hovedregel.vilkårskode

                val likestiltYtelse = this.finn(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE)
                if (likestiltYtelse != null && likestiltYtelse.erOppfylt()) {
                    val ikkeAAPFørForeldrepenger = this.finn(Vilkårskode.OPPTJENING_YRKESAKTIV_FØR_FORELDREPENGER)
                    if (ikkeAAPFørForeldrepenger != null) {
                        return ikkeAAPFørForeldrepenger.vilkårskode
                    }
                }
                return null
            }
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
