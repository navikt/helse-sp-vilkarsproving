package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.speil.backend.app.rest.TransaksjonProvider

/**
 * Kjører arbeidet rett mot in-memory-lagrene.
 *
 * Har ingen rollback — det er bevisst: transaksjonell oppførsel testes mot en ekte database
 * (`infra/db`), mens disse testene handler om domenelogikken.
 */
internal class InMemoryTransaksjonProvider(
    override val kravprøvinger: InMemoryKravprøvingRepository = InMemoryKravprøvingRepository(),
    override val kravvurderinger: InMemoryKravvurderingRepository = InMemoryKravvurderingRepository(),
) : TransaksjonProvider<Transaksjonskontekst>,
    Transaksjonskontekst {
    override fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T = block(this)
}
