package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsprøving
import java.time.LocalDate

internal interface VilkårsprøvingRepository {
    fun lagre(prøving: Vilkårsprøving)

    fun finnSiste(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Vilkårsprøving?
}
