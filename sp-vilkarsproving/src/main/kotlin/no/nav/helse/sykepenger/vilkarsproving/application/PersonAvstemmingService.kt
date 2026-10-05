package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.loggInfo

internal class PersonAvstemmingService(
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val spleisClient: ISpleisClient,
) {
    fun lagreOpptjeningsvurderinger(fødselsnummer: String): Int {
        if (transaksjonProvider.transaksjon { it.migreringslogg.finnesFor(fødselsnummer) }) {
            loggInfo("Person-avstemming er allerede behandlet")
            return 0
        }

        val spleisVurderinger = spleisClient.hentOpptjeningsvurderinger(fødselsnummer)
        val vurderinger = spleisVurderinger.map { it.tilOpptjeningsvurdering(fødselsnummer) }

        val (antallLagret, antallHoppetOver) =
            transaksjonProvider.transaksjon { kontekst ->
                var antallLagret = 0
                var antallHoppetOver = 0
                vurderinger.forEach { vurdering ->
                    val eksisterende = kontekst.opptjeningsvurderinger.finn(vurdering.id)
                    if (eksisterende == null) {
                        kontekst.opptjeningsvurderinger.lagre(vurdering)
                        antallLagret++
                    } else {
                        check(eksisterende.fødselsnummer == fødselsnummer) {
                            "Opptjeningsvurdering ${vurdering.id} er allerede lagret for en annen person"
                        }
                        antallHoppetOver++
                    }
                }
                kontekst.migreringslogg.lagre(
                    fødselsnummer = fødselsnummer,
                    antallVurderinger = spleisVurderinger.size,
                    antallHoppetOver = antallHoppetOver,
                )
                antallLagret to antallHoppetOver
            }

        loggInfo(
            "Behandlet person-avstemming",
            "antallOpptjeningsvurderinger" to spleisVurderinger.size.toString(),
            "antallNyeOpptjeningsvurderinger" to antallLagret.toString(),
            "antallHoppetOverOpptjeningsvurderinger" to antallHoppetOver.toString(),
        )
        return antallLagret
    }
}
