package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import java.time.LocalDate

internal class InMemoryOpptjeningsprøvingRepository : OpptjeningsprøvingRepository {
    private val prøvinger = mutableListOf<Opptjeningsprøving>()

    internal val allePrøvinger: List<Opptjeningsprøving> get() = prøvinger.toList()

    override fun lagre(prøving: Opptjeningsprøving) {
        val eksisterende = prøvinger.indexOfFirst { it.id == prøving.id }
        if (eksisterende != -1) {
            prøvinger[eksisterende] = prøving
            return
        }
        check(prøvinger.none { it.gjelderSammeSom(prøving) && !it.erAvsluttet }) {
            "Det pågår allerede en opptjeningsprøving for fødselsnummer ${prøving.fødselsnummer} med skjæringstidspunkt ${prøving.skjæringstidspunkt}"
        }
        prøvinger.add(prøving)
    }

    override fun finnSiste(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = prøvinger.lastOrNull { it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }

    private fun Opptjeningsprøving.gjelderSammeSom(annen: Opptjeningsprøving) = fødselsnummer == annen.fødselsnummer && skjæringstidspunkt == annen.skjæringstidspunkt
}
