package no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth

data class SaksbehandlerPrincipal(
    val saksbehandler: Saksbehandler,
    val tilganger: Set<Tilgang>,
    val accessToken: AccessToken,
)
