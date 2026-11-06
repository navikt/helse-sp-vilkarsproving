package no.nav.helse.sykepenger.vilkarsproving.application

internal interface Outbox {
    /** `melding` er hele rapid-meldingen som JSON. Den publiseres uendret, med fødselsnummeret som nøkkel. */
    fun leggTil(
        fødselsnummer: String,
        melding: String,
    )
}
