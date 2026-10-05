package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.personpseudoid.ValkeyPersonPseudoIdProvider
import com.github.navikt.tbd_libs.populasjonstilgang.client.PopulasjonstilgangConfig
import com.github.navikt.tbd_libs.populasjonstilgang.client.tilgangsmaskinenClient
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OutboxPubliseringsjobb
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.PostManuellVilkårsvurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClient
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.AzureAdConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.TilgangsgrupperTilTilganger
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.configureJwtAuthentication
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.db.DatabaseConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.db.dataSource
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.db.migrerSynkront
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi.OpenApiConfig
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi.configureOpenApiPlugin
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureCallId
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureCallLogging
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureStatusPages
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestRuting
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.configureRestRuting
import no.nav.sykepenger.libs.logging.loggInfo

private const val APP_NAVN = "sp-vilkarsproving"

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

fun main() {
    val env = System.getenv()
    val versjonAvKode = env.getValue("NAIS_APP_IMAGE")
    val konfigurasjon = AppKonfigurasjon.fraEnv(APP_NAVN, env)
    val spleisClient = SpleisClient.fromEnv()

    val dataSource = konfigurasjon.database.dataSource()
    migrerSynkront(konfigurasjon.database)
    val transaksjonProvider = PostgresTransaksjonProvider(dataSource)

    RapidApplication
        .create(env, builder = {
            withKtorModule {
                ktorApp(
                    konfigurasjon = konfigurasjon,
                    transaksjonProvider = transaksjonProvider,
                    spleisClient = spleisClient,
                    env = env,
                )
            }
        })
        .apply {
            GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(
                rapidsConnection = this,
                transaksjonProvider = transaksjonProvider,
                versjonAvKode = versjonAvKode,
            )
            OpptjeningsvurderingRiver(
                rapidsConnection = this,
                transaksjonProvider = transaksjonProvider,
                versjonAvKode = versjonAvKode,
            )
            OpptjeningsvurderingResultatRiver(
                rapidsConnection = this,
                transaksjonProvider = transaksjonProvider,
                spleisClient = spleisClient,
            )
           /* PersonAvstemmingRiver(
                rapidsConnection = this,
                personAvstemmingService =
                    PersonAvstemmingService(
                        transaksjonProvider = transaksjonProvider,
                        spleisClient = spleisClient,
                    ),
            )*/
            register(
                OutboxPubliseringsjobb(
                    rapidsConnection = this,
                    transaksjonProvider = transaksjonProvider,
                ),
            )
        }.start()
}

private fun Application.ktorApp(
    konfigurasjon: AppKonfigurasjon,
    transaksjonProvider: TransaksjonProvider<Transaksjonskontekst>,
    spleisClient: ISpleisClient,
    env: Map<String, String>,
) {
    val texasClient = TexasClient.fromEnv()
    val restAdapter =
        RestAdapter<Transaksjonskontekst>(
            personPseudoIdProvider = ValkeyPersonPseudoIdProvider.fraEnv(konfigurasjon.valkeyInstansPersonPseudoId, env),
            populasjonstilgangskontrollProvider = konfigurasjon.populasjonstilgang.tilgangsmaskinenClient(texasClient),
            transaksjonProvider = transaksjonProvider,
        )
    configureCallId()
    configureCallLogging()
    configureContentNegotiation()
    configureStatusPages()
    configureResources()
    configureJwtAuthentication(
        azureAdConfig = konfigurasjon.azureAd,
        tilgangsgrupperTilTilganger = konfigurasjon.tilganger,
    )
    configureOpenApiPlugin(konfigurasjon.openApi)
    configureRestRuting(restAdapter, endepunkter(spleisClient = spleisClient))
    monitor.subscribe(ApplicationStarted) {
        loggInfo("Ktor-applikasjon startet", "appNavn" to konfigurasjon.appNavn)
    }
}

/**
 * Appens HTTP-endepunkter, definert ett sted. Både produksjonsappen ([main]) og LocalApp bruker
 * denne, slik at et nytt endepunkt bare trenger å registreres her for å bli med begge steder.
 */
internal fun endepunkter(spleisClient: ISpleisClient): RestRuting<Transaksjonskontekst>.() -> Unit =
    {
        get(GetVilkårsvurderingerForPersonBehandler(SpleisOpptjeningsvurderingService(spleisClient)))
        post(PostManuellVilkårsvurderingBehandler())
    }
