package no.nav.helse.sykepenger.vilkarsproving.application

internal interface Transaksjonskontekst {
    val opptjeningsprøvinger: OpptjeningsprøvingRepository
    val opptjeningsvurderinger: OpptjeningsvurderingRepository
}
