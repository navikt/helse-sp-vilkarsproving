package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilOpptjeningsvurdering

/**
 * Henter en opptjeningsvurdering direkte fra spleis-api og oversetter den til domeneobjektet
 * [Opptjeningsvurdering], for de tilfellene vurderingen ikke finnes i vår egen database — typisk
 * vurderinger gjort før vi begynte å lagre dem selv, eller vurderinger overtatt fra Infotrygd.
 */
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
