package no.nav.helse.sykepenger.vilkarsproving.application

internal interface Transaksjonskontekst {
    val vilkårsprøvinger: VilkårsprøvingRepository
    val vilkårsvurderinger: VilkårsvurderingRepository
}
