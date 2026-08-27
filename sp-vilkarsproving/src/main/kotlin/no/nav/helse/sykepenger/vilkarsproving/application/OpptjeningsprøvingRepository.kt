package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import java.time.LocalDate

internal interface OpptjeningsprøvingRepository {
    fun lagre(prøving: Opptjeningsprøving)

    fun finnSiste(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Opptjeningsprøving?
}
