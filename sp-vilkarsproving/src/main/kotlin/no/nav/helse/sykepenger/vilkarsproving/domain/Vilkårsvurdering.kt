package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

internal data class Vilkårsvurdering(
    val id: VilkårsvurderingId,
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val vurdertTidspunkt: Instant,
    val kilde: Vurderingskilde,
    val vurderingskilde: Opptjeningsvurderingskilde,
    val lovreferanse: Lovreferanse,
) {
    private fun erOppfylt() = utfall == Utfall.Oppfylt

    companion object {
        fun List<Vilkårsvurdering>.finn(vilkårskode: Vilkårskode): Vilkårsvurdering? = this.find { it.vilkårskode == vilkårskode }

        // Scenario A–E: se docs/opptjening-8-2-kodeverk.md
        fun List<Vilkårsvurdering>.avgjørendeVilkårsvurdering(): Vilkårsvurdering? {
            val hovedregel = this.finn(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER)
            if (hovedregel?.erOppfylt() == true) return hovedregel

            val førstePunktum = this.finn(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER)
            if (førstePunktum?.erOppfylt() != true) return null

            // Andre punktum begrenser første punktum: foreldrepenger opptjent på AAP gir ikke rett alene
            val andrePunktum = this.finn(Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP)
            if (andrePunktum == null || andrePunktum.erOppfylt()) return førstePunktum

            // Tredje punktum er unntaket fra andre punktum
            return this.finn(Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER)?.takeIf { it.erOppfylt() }
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
            vurderingskilde = Opptjeningsvurderingskilde.VURDERT_I_SP_VILKARSPROVING,
            lovreferanse = vilkårsutfall.lovreferanse,
        )

        fun avSaksbehandler(
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            saksbehandlerIdent: String,
            fritekstbegrunnelse: String,
            journalpostId: List<String> = emptyList(),
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = utfall,
            vurdertTidspunkt = Instant.now(),
            kilde = Vurderingskilde.Saksbehandler(saksbehandlerIdent, fritekstbegrunnelse, journalpostId),
            vurderingskilde = Opptjeningsvurderingskilde.VURDERT_I_SP_VILKARSPROVING,
            lovreferanse = vilkårskode.lovreferanse,
        )

        fun overførtFraSpleis(
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            grunnlag: Opptjeningsgrunnlag,
            utledetFakta: UtledetFakta,
            vurdertTidspunkt: Instant,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = utfall,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = Vurderingskilde.OverførtFraSpleis(grunnlag, utledetFakta),
            vurderingskilde = Opptjeningsvurderingskilde.OVERFORT_FRA_SPLEIS,
            lovreferanse = vilkårskode.lovreferanse,
        )

        fun fraLagring(
            id: VilkårsvurderingId,
            vilkårskode: Vilkårskode,
            utfall: Utfall,
            vurdertTidspunkt: Instant,
            kilde: Vurderingskilde,
            vurderingskilde: Opptjeningsvurderingskilde,
            lovreferanse: Lovreferanse,
        ) = Vilkårsvurdering(id, vilkårskode, utfall, vurdertTidspunkt, kilde, vurderingskilde, lovreferanse)
    }
}
