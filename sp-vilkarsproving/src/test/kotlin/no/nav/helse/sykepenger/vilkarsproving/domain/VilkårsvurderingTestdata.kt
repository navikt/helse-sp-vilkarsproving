package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

internal fun Vilkårsvurdering.Companion.avSaksbehandler(
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
