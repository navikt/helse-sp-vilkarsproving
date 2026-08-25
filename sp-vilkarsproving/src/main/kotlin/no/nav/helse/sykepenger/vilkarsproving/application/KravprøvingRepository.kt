package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravprøving
import java.time.LocalDate

internal interface KravprøvingRepository {
    fun lagre(prøving: Kravprøving)

    fun finnSiste(
        krav: Krav,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Kravprøving?
}
