package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import java.time.LocalDate

internal class InMemoryOpptjeningsvurderingRepository : OpptjeningsvurderingRepository {
    private val vurderinger = mutableListOf<Opptjeningsvurdering>()

    internal val alleVurderinger: List<Opptjeningsvurdering> get() = vurderinger.toList()
    internal val antallLagringer get() = vurderinger.size

    override fun lagre(vurdering: Opptjeningsvurdering) {
        check(vurderinger.none { it.id == vurdering.id }) { "Vurdering ${vurdering.id} er allerede lagret. Vurderinger er immutable." }
        vurderinger.add(vurdering)
    }

    override fun gjeldende(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = vurderinger.lastOrNull { it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }

    override fun finn(opptjeningsvurderingId: OpptjeningsvurderingId) = vurderinger.firstOrNull { it.id == opptjeningsvurderingId }
}

