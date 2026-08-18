package no.nav.helse.sykepenger.vilkarsproving.application

internal interface Transaksjonskontekst {
    val vilkårsprøvinger: VilkårsprøvingRepository
    val vilkårsvurderinger: VilkårsvurderingRepository
}

internal interface TransaksjonProvider {
    fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T
}
