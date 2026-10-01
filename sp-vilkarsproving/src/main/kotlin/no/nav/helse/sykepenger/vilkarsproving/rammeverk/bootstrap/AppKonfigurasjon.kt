package no.nav.helse.sykepenger.vilkarsproving.rammeverk.bootstrap

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.AzureAdConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.TilgangsgrupperTilTilganger
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.db.DatabaseConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi.OpenApiConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.PopulasjonstilgangConfig

data class AppKonfigurasjon(
    val appNavn: String,
    val azureAd: AzureAdConfig,
    val database: DatabaseConfig,
    val populasjonstilgang: PopulasjonstilgangConfig,
    val tilganger: TilgangsgrupperTilTilganger,
    val openApi: OpenApiConfig,
    val valkeyInstansPersonPseudoId: String = "personpseudoid",
) {
    companion object {
        fun fraEnv(
            appNavn: String,
            env: Map<String, String> = System.getenv(),
        ) = AppKonfigurasjon(
            appNavn = appNavn,
            azureAd = AzureAdConfig.fraEnv(env),
            database = DatabaseConfig.fraEnv(env),
            populasjonstilgang = PopulasjonstilgangConfig.fraEnv(env),
            tilganger = TilgangsgrupperTilTilganger.fraEnv(env),
            openApi = OpenApiConfig.fraEnv(appNavn, env),
        )
    }
}
