package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

interface ApiErrorCode {
    val httpStatus: Int
    val tittel: String
}
