package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import java.time.LocalDate

internal class InMemoryKravvurderingRepository : KravvurderingRepository {
    private val vurderinger = mutableListOf<Kravvurdering>()

    internal val alleVurderinger: List<Kravvurdering> get() = vurderinger.toList()
    internal val antallLagringer get() = vurderinger.size

    override fun lagre(vurdering: Kravvurdering) {
        check(vurderinger.none { it.id == vurdering.id }) { "Vurdering ${vurdering.id} er allerede lagret. Vurderinger er immutable." }
        vurderinger.add(vurdering)
    }

    override fun gjeldende(
        krav: Krav,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = vurderinger.lastOrNull { it.krav == krav && it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }

    override fun finn(
        krav: Krav,
        kravvurderingId: KravvurderingId,
    ) = vurderinger.firstOrNull { it.krav == krav && it.id == kravvurderingId }
}
