package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import java.time.LocalDate

internal interface OpptjeningsvurderingRepository {
    fun lagre(vurdering: Opptjeningsvurdering)

    fun gjeldende(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Opptjeningsvurdering?

    fun finn(opptjeningsvurderingId: OpptjeningsvurderingId): Opptjeningsvurdering?
}
