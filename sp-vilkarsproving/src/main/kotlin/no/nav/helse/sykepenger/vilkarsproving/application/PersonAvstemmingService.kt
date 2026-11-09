package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.TransaksjonProvider
import no.nav.sykepenger.libs.logging.loggInfo
import no.nav.sykepenger.libs.logging.loggWarn
import java.time.Instant

internal class PersonAvstemmingService(
    private val transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    private val spleisClient: ISpleisClient,
) {

    fun lagreOpptjeningsvurderinger(fødselsnummer: String): Int {
        if (transaksjonProvider.transaksjon { it.migreringslogg.finnesFor(fødselsnummer) }) {
            loggInfo("Person-avstemming er allerede behandlet")
            return 0
        }
        return avstem(fødselsnummer)
    }

    fun avstem(fødselsnummer: String): Int {
        val spleisVurderinger = spleisClient.hentOpptjeningsvurderinger(fødselsnummer)

        val (import, antallNyeKoblinger) =
            transaksjonProvider.transaksjon { kontekst ->
                val import =
                    if (kontekst.migreringslogg.finnesFor(fødselsnummer)) {
                        Import.ALLEREDE_IMPORTERT
                    } else {
                        importerVurderinger(kontekst, fødselsnummer, spleisVurderinger)
                    }
                import to backfillKoblinger(kontekst.opptjeningsvurderinger, fødselsnummer, spleisVurderinger)
            }

        loggInfo(
            "Behandlet person-avstemming",
            "antallOpptjeningsvurderinger" to spleisVurderinger.size.toString(),
            "antallNyeOpptjeningsvurderinger" to import.antallLagret.toString(),
            "antallHoppetOverOpptjeningsvurderinger" to import.antallHoppetOver.toString(),
            "antallNyeKoblinger" to antallNyeKoblinger.toString(),
        )
        return import.antallLagret
    }

    private data class Import(
        val antallLagret: Int,
        val antallHoppetOver: Int,
    ) {
        companion object {
            val ALLEREDE_IMPORTERT = Import(antallLagret = 0, antallHoppetOver = 0)
        }
    }

    private fun importerVurderinger(
        kontekst: Transaksjonskontekst,
        fødselsnummer: String,
        spleisVurderinger: List<SpleisOpptjeningsvurdering>,
    ): Import {
        var antallLagret = 0
        var antallHoppetOver = 0
        tilOpptjeningsvurderinger(fødselsnummer, spleisVurderinger).forEach { vurdering ->
            val eksisterende = kontekst.opptjeningsvurderinger.finn(vurdering.id)
            if (eksisterende == null) {
                kontekst.opptjeningsvurderinger.lagre(vurdering)
                antallLagret++
            } else {
                sjekkSammePerson(eksisterende, fødselsnummer)
                antallHoppetOver++
            }
        }
        kontekst.migreringslogg.lagre(
            fødselsnummer = fødselsnummer,
            antallVurderinger = spleisVurderinger.size,
            antallHoppetOver = antallHoppetOver,
        )
        return Import(antallLagret, antallHoppetOver)
    }

    private fun tilOpptjeningsvurderinger(
        fødselsnummer: String,
        spleisVurderinger: List<SpleisOpptjeningsvurdering>,
    ): List<Opptjeningsvurdering> =
        try {
            spleisVurderinger.map { it.tilOpptjeningsvurdering(fødselsnummer) }
        } catch (ex: IllegalStateException) {
            loggWarn(
                "Ugyldige opptjeningsvurderinger i svaret fra Spleis",
                ex,
                "spleisRespons" to spleisVurderinger.toString(),
            )
            throw ex
        }

    private fun backfillKoblinger(
        repository: OpptjeningsvurderingRepository,
        fødselsnummer: String,
        spleisVurderinger: List<SpleisOpptjeningsvurdering>,
    ): Int =
        spleisVurderinger.sumOf { spleisVurdering ->
            val vurdering = repository.finn(spleisVurdering.opptjeningsvurderingId) ?: return@sumOf 0
            sjekkSammePerson(vurdering, fødselsnummer)
            val erstatning = nyesteMedSammeKategoriOgSkjæringstidspunkt(repository, vurdering)
            spleisVurdering.rettighetskategorier.sumOf { rettighetskategori ->
                val antallForVurderingen = repository.kobleHvisMangler(vurdering, rettighetskategori.id, rettighetskategori.opprettet)
                val antallForErstatningen =
                    erstatning?.let {
                        repository.kobleHvisMangler(it, rettighetskategori.id, maxOf(rettighetskategori.opprettet, it.vurdertTidspunkt))
                    } ?: 0
                antallForVurderingen + antallForErstatningen
            }
        }

    private fun sjekkSammePerson(
        vurdering: Opptjeningsvurdering,
        fødselsnummer: String,
    ) = check(vurdering.fødselsnummer == fødselsnummer) {
        "Opptjeningsvurdering ${vurdering.id} er allerede lagret for en annen person"
    }

    private fun nyesteMedSammeKategoriOgSkjæringstidspunkt(
        repository: OpptjeningsvurderingRepository,
        vurdering: Opptjeningsvurdering,
    ): Opptjeningsvurdering? =
        repository
            .historikk(vurdering.fødselsnummer, vurdering.skjæringstidspunkt)
            .firstOrNull { it.kategori == vurdering.kategori }
            ?.takeUnless { it.id == vurdering.id }

    private fun OpptjeningsvurderingRepository.kobleHvisMangler(
        vurdering: Opptjeningsvurdering,
        rettighetskategoriId: RettighetskategoriId,
        registrert: Instant,
    ): Int {
        if (rettighetskategoriId in rettighetskategoriIder(vurdering.id)) return 0
        koble(vurdering.id, setOf(rettighetskategoriId), registrert)
        return 1
    }
}
