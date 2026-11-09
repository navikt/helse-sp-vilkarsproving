package no.nav.helse.sykepenger.vilkarsproving.domain

/** Hovedkategorien i en rettighetskategori fra sp-rettighetskategori. Den avgjør kategorien til opptjening. */
internal sealed interface Hovedkategori {
    data object Arbeidstaker : Hovedkategori

    data object SelvstendigNæringsdrivende : Hovedkategori

    data object ArbeidstakerOgSelvstendigNæringsdrivende : Hovedkategori

    /** Hovedkategorien mangler i svaret, eller har en verdi vi ikke kjenner. */
    data class Ukjent(
        val verdi: String?,
    ) : Hovedkategori
}
