package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilOpptjeningsvurdering

internal class SpleisOpptjeningsvurderingService(
    private val spleisClient: ISpleisClient,
) {
    fun finn(
        opptjeningsvurderingId: OpptjeningsvurderingId,
        fødselsnummer: String,
    ): Opptjeningsvurdering? =
        spleisClient
            .hentOpptjeningsvurderinger(fødselsnummer)
            .find { it.opptjeningsvurderingId == opptjeningsvurderingId }
            ?.tilOpptjeningsvurdering(fødselsnummer)
}
