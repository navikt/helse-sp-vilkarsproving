package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsprøving
import java.time.LocalDate

internal class InMemoryVilkårsprøvingRepository : VilkårsprøvingRepository {
    private val prøvinger = mutableListOf<Vilkårsprøving>()

    internal val alleProvinger: List<Vilkårsprøving> get() = prøvinger.toList()

    override fun lagre(prøving: Vilkårsprøving) {
        val eksisterende = prøvinger.indexOfFirst { it.id == prøving.id }
        if (eksisterende != -1) {
            prøvinger[eksisterende] = prøving
            return
        }
        check(prøvinger.none { it.gjelderSammeSom(prøving) && !it.erAvsluttet }) {
            "Det pågår allerede en prøving av ${prøving.vilkår} for fødselsnummer ${prøving.fødselsnummer} med skjæringstidspunkt ${prøving.skjæringstidspunkt}"
        }
        prøvinger.add(prøving)
    }

    override fun finnSiste(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ) = prøvinger.lastOrNull { it.vilkår == vilkår && it.fødselsnummer == fødselsnummer && it.skjæringstidspunkt == skjæringstidspunkt }

    private fun Vilkårsprøving.gjelderSammeSom(annen: Vilkårsprøving) = vilkår == annen.vilkår && fødselsnummer == annen.fødselsnummer && skjæringstidspunkt == annen.skjæringstidspunkt
}
