package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import java.time.LocalDate

internal interface KravvurderingRepository {
    fun lagre(vurdering: Kravvurdering)

    fun gjeldende(
        krav: Krav,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Kravvurdering?

    fun finn(
        krav: Krav,
        kravvurderingId: KravvurderingId,
    ): Kravvurdering?
}
