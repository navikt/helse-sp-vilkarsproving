package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import java.time.LocalDate

internal sealed class VurderOpptjeningResultat {
    data class HarVurdering(
        val fødselsnummer: String,
        val skjæringstidspunkt: LocalDate,
        val kravvurderingId: KravvurderingId,
    ) : VurderOpptjeningResultat()

    data class TrengerArbeidsforhold(
        val fødselsnummer: String,
        val skjæringstidspunkt: LocalDate,
    ) : VurderOpptjeningResultat()
}
