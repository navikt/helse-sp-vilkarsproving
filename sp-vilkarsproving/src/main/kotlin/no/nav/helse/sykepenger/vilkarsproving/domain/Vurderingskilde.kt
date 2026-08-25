package no.nav.helse.sykepenger.vilkarsproving.domain

internal sealed interface Vurderingskilde {
    data class Automatisk(
        val prøvingId: PrøvingId,
        val grunnlag: Vilkårsgrunnlag,
        val utledetFakta: UtledetFakta,
        val versjonAvKildekode: String,
    ) : Vurderingskilde

    data class Saksbehandler(
        val ident: String,
        val fritekstbegrunnelse: String,
    ) : Vurderingskilde

    data class OverførtFraSpleis(
        val grunnlag: Vilkårsgrunnlag,
        val utledetFakta: UtledetFakta,
    ) : Vurderingskilde
}
