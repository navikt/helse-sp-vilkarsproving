package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravprøving
import java.time.LocalDate

internal class InMemoryKravprøvingRepository : KravprøvingRepository {
    private val prøvinger = mutableListOf<Kravprøving>()

    internal val allePrøvinger: List<Kravprøving> get() = prøvinger.toList()

    override fun lagre(prøving: Kravprøving) {
        val eksisterende = prøvinger.indexOfFirst { it.id == prøving.id }
        if (eksisterende != -1) {
            prøvinger[eksisterende] = prøving
            return
        }
        check(prøvinger.none { it.gjelderSammeSom(prøving) && !it.erAvsluttet }) {
            "Det pågår allerede en prøving av ${prøving.krav} for fødselsnummer ${prøving.fødselsnummer} med skjæringstidspunkt ${prøving.skjæringstidspunkt}"
        }
        prøvinger.add(prøving)
    }

    override fun finnSiste(
        krav: Krav,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = prøvinger.lastOrNull { it.krav == krav && it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }

    private fun Kravprøving.gjelderSammeSom(annen: Kravprøving) = krav == annen.krav && fødselsnummer == annen.fødselsnummer && skjæringstidspunkt == annen.skjæringstidspunkt
}
