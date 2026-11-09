package no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori

import no.nav.helse.sykepenger.vilkarsproving.domain.Hovedkategori
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import java.util.UUID

/**
 * En enkel fake for sp-rettighetskategori, for tester som ikke handler om selve oppslaget.
 * Ukjente ID-er gir [standard]. Bruk [RettighetskategoriWiremock] når oppslaget er det som testes.
 */
internal class FakeRettighetskategoriClient(
    val standard: Hovedkategori = Hovedkategori.Arbeidstaker,
) : IRettighetskategoriClient {
    private val hovedkategorier = mutableMapOf<RettighetskategoriId, Hovedkategori>()

    fun registrer(
        rettighetskategoriId: UUID,
        hovedkategori: Hovedkategori,
    ) {
        hovedkategorier[RettighetskategoriId(rettighetskategoriId)] = hovedkategori
    }

    override fun hentHovedkategori(
        rettighetskategoriId: RettighetskategoriId,
        fødselsnummer: String,
    ) = hovedkategorier[rettighetskategoriId] ?: standard
}

/** Hovedkategorien som svarer til `arbeidssituasjon` i eldre testbehov. */
internal fun hovedkategori(arbeidssituasjon: String): Hovedkategori =
    when (arbeidssituasjon) {
        "Arbeidstaker" -> Hovedkategori.Arbeidstaker
        "SelvstendigNæringsdrivende" -> Hovedkategori.SelvstendigNæringsdrivende
        else -> error("Ukjent arbeidssituasjon i testen: $arbeidssituasjon")
    }
