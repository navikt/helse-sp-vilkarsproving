package no.nav.helse.sykepenger.vilkarsproving.rammeverk.bootstrap

import com.github.navikt.tbd_libs.access_token.TexasClient
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auditlogg.Auditlogger
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.TilgangsgrupperTilBrukerroller
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.configureJwtAuthentication
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.db.dataSource
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.db.migrerSynkront
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi.configureOpenApiPlugin
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.PersonPseudoIdProvider
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.ValkeyPersonPseudoIdProvider
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.tilgangsmaskinenClient
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
import javax.sql.DataSource

fun <ROLLE : Brukerrolle, TRANSAKSJON> startApp(
    konfigurasjon: AppKonfigurasjon,
    brukerroller: TilgangsgrupperTilBrukerroller<ROLLE>,
    transaksjonProvider: (DataSource) -> TransaksjonProvider<TRANSAKSJON>,
    env: Map<String, String> = System.getenv(),
    rivere: RapidsConnection.(TransaksjonProvider<TRANSAKSJON>) -> Unit = {},
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit = {},
) {
    val dataSource = konfigurasjon.database.dataSource()

    migrerSynkront(konfigurasjon.database)

    RapidApplication
        .create(env, builder = {
            withKtorModule {
                ktorApp(
                    konfigurasjon = konfigurasjon,
                    brukerroller = brukerroller,
                    transaksjonProvider = transaksjonProvider(dataSource),
                    endepunkter = endepunkter,
                    env = env,
                )
            }
        })
        .apply { rivere(transaksjonProvider(dataSource)) }
        .start()
}

private fun <ROLLE : Brukerrolle, TRANSAKSJON> Application.ktorApp(
    konfigurasjon: AppKonfigurasjon,
    brukerroller: TilgangsgrupperTilBrukerroller<ROLLE>,
    transaksjonProvider: TransaksjonProvider<TRANSAKSJON>,
    endepunkter: RestRuting<ROLLE, TRANSAKSJON>.() -> Unit,
    env: Map<String, String>,
) {
    val texasClient = TexasClient.fromEnv()
    val populasjonstilgangskontrollProvider = konfigurasjon.populasjonstilgang.tilgangsmaskinenClient(texasClient)
    val personPseudoIdProvider: PersonPseudoIdProvider =
        ValkeyPersonPseudoIdProvider.fraEnv(konfigurasjon.valkeyInstansPersonPseudoId, env)
    val auditlogger = Auditlogger(konfigurasjon.appNavn)
    val restAdapter =
        RestAdapter<ROLLE, TRANSAKSJON>(
            personPseudoIdProvider = personPseudoIdProvider,
            populasjonstilgangskontrollProvider = populasjonstilgangskontrollProvider,
            auditlogger = auditlogger,
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
        tilgangsgrupperTilBrukerroller = brukerroller,
    )
    configureOpenApiPlugin(konfigurasjon.openApi)
    configureRestRuting(restAdapter, endepunkter)
    monitor.subscribe(ApplicationStarted) {
        loggInfo("Ktor-applikasjon startet", "appNavn" to konfigurasjon.appNavn)
    }
}
