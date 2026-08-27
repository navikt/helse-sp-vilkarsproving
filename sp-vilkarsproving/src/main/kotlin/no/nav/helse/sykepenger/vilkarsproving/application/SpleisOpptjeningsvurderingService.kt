package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilKravvurdering

/**
 * Henter en opptjeningsvurdering direkte fra spleis-api og oversetter den til domeneobjektet
 * [Kravvurdering], for de tilfellene vurderingen ikke finnes i vår egen database — typisk
 * vurderinger gjort før vi begynte å lagre dem selv, eller vurderinger overtatt fra Infotrygd.
 */
internal class SpleisOpptjeningsvurderingService(
    private val spleisClient: ISpleisClient,
) {
    fun finn(
        kravvurderingId: KravvurderingId,
        fødselsnummer: String,
    ): Kravvurdering? =
        spleisClient
            .hentOpptjeningsvurderinger(fødselsnummer)
            .find { it.opptjeningsvurderingId == kravvurderingId }
            ?.tilKravvurdering(fødselsnummer)
}
