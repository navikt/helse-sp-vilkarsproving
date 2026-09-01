package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.speil.backend.app.rest.TransaksjonProvider

internal class InMemoryTransaksjonProvider(
    override val opptjeningsprøvinger: InMemoryOpptjeningsprøvingRepository = InMemoryOpptjeningsprøvingRepository(),
    override val opptjeningsvurderinger: InMemoryOpptjeningsvurderingRepository = InMemoryOpptjeningsvurderingRepository(),
) : TransaksjonProvider<Transaksjonskontekst>,
    Transaksjonskontekst {
    override fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T = block(this)
}
