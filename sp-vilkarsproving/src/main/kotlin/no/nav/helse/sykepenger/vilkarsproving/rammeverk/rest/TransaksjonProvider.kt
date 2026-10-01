package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

interface TransaksjonProvider<TRANSAKSJON> {
    fun <T> transaksjon(block: (TRANSAKSJON) -> T): T
}
