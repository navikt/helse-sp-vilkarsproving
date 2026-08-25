package no.nav.helse.sykepenger.vilkarsproving.application

internal interface Transaksjonskontekst {
    val kravprøvinger: KravprøvingRepository
    val kravvurderinger: KravvurderingRepository
}
