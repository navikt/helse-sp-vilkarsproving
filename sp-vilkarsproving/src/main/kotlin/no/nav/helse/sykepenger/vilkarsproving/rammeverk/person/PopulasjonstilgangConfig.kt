package no.nav.helse.sykepenger.vilkarsproving.rammeverk.person

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.client.TilgangsmaskinenClient

data class PopulasjonstilgangConfig(
    val scope: String,
    val baseUrl: String,
) {
    companion object {
        fun fraEnv(env: Map<String, String> = System.getenv()) =
            PopulasjonstilgangConfig(
                scope = env.getValue("TILGANGSMASKINEN_SCOPE"),
                baseUrl = env.getValue("TILGANGSMASKINEN_BASE_URL"),
            )
    }
}

fun PopulasjonstilgangConfig.tilgangsmaskinenClient(
    tokenProvider: TexasClient = TexasClient.fromEnv(),
): PopulasjonstilgangskontrollProvider =
    TilgangsmaskinenClient(
        scope = scope,
        baseUrl = baseUrl,
        tokenProvider = tokenProvider,
    )
