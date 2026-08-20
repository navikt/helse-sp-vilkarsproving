package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import java.time.LocalDate

internal class InMemoryVilkårsvurderingRepository : VilkårsvurderingRepository {
    private val vurderinger = mutableListOf<Vilkårsvurdering>()

    internal val alleVurderinger: List<Vilkårsvurdering> get() = vurderinger.toList()
    internal val antallLagringer get() = vurderinger.size

    override fun lagre(vurdering: Vilkårsvurdering) {
        check(vurderinger.none { it.id == vurdering.id }) { "Vurdering ${vurdering.id} er allerede lagret. Vurderinger er immutable." }
        vurderinger.add(vurdering)
    }

    override fun gjeldende(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = vurderinger.lastOrNull { it.vilkår == vilkår && it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }

    override fun finn(
        vilkår: Vilkår,
        vurderingId: VurderingId,
    ) = vurderinger.firstOrNull { it.vilkår == vilkår && it.id == vurderingId }

    override fun finnAlle(fødselsnummer: String): List<Vilkårsvurdering> = vurderinger.filter { it.fødselsnummer == fødselsnummer }
}
