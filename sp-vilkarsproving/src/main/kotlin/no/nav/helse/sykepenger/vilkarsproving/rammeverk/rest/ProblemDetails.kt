package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

/** RFC 7807 ("Problem Details for HTTP APIs") problem+json-respons. */
data class ProblemDetails(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String,
)
