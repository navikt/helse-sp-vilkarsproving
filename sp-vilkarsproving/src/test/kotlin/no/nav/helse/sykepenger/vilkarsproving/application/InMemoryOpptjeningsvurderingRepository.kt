package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import java.time.Instant
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

    override fun historikk(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = vurderinger
        .filter { it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }
        .asReversed()
        .sortedByDescending { it.vurdertTidspunkt }

    override fun finn(opptjeningsvurderingId: OpptjeningsvurderingId) = vurderinger.firstOrNull { it.id == opptjeningsvurderingId }

    override fun koble(
        opptjeningsvurderingId: OpptjeningsvurderingId,
        rettighetskategoriIder: Set<RettighetskategoriId>,
        registrert: Instant,
    ) {
        checkNotNull(finn(opptjeningsvurderingId)) { "Vurdering $opptjeningsvurderingId finnes ikke" }
        rettighetskategoriIder.forEach { rettighetskategoriId ->
            if (_koblinger.none { it.opptjeningsvurderingId == opptjeningsvurderingId && it.rettighetskategoriId == rettighetskategoriId }) {
                _koblinger.add(Kobling(opptjeningsvurderingId, rettighetskategoriId, registrert))
            }
        }
    }

    // Som i Postgres vinner den sist lagrede vurderingen når vurderingstidspunktene er like
    override fun nyesteKobletTil(rettighetskategoriId: RettighetskategoriId) =
        _koblinger
            .filter { it.rettighetskategoriId == rettighetskategoriId }
            .mapNotNull { finn(it.opptjeningsvurderingId) }
            .maxWithOrNull(compareBy({ it.vurdertTidspunkt }, { vurderinger.indexOf(it) }))

    override fun rettighetskategoriIder(opptjeningsvurderingId: OpptjeningsvurderingId) =
        _koblinger
            .filter { it.opptjeningsvurderingId == opptjeningsvurderingId }
            .map { it.rettighetskategoriId }
            .toSet()

    private val _koblinger = mutableListOf<Kobling>()
    internal val koblinger: List<Kobling> get() = _koblinger.toList()

    internal data class Kobling(
        val opptjeningsvurderingId: OpptjeningsvurderingId,
        val rettighetskategoriId: RettighetskategoriId,
        val registrert: Instant,
    )
}
