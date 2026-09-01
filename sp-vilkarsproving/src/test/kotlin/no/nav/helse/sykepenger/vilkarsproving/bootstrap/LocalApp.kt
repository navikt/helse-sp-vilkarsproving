package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.AZURE_AD_AUTHENTICATION_NAME
import no.nav.helse.speil.backend.app.auth.AccessToken
import no.nav.helse.speil.backend.app.auth.NavIdent
import no.nav.helse.speil.backend.app.auth.Saksbehandler
import no.nav.helse.speil.backend.app.auth.SaksbehandlerOid
import no.nav.helse.speil.backend.app.auth.SaksbehandlerPrincipal
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.openapi.OpenApiConfig
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.configureRestRuting
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.speil.backend.app.testfixtures.installTestPlugins
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import java.util.UUID

private const val PORT = 8181

fun main() {
    val server =
        embeddedServer(CIO, port = PORT) {
            installTestPlugins(
                openApiConfig = OpenApiConfig(eksponerOpenApi = true, tittel = "sp-vilkarsproving"),
            )
            installerLokalAutentisering()
            configureRestRuting(
                restAdapter =
                    RestAdapter<AppRolle, Transaksjonskontekst>(
                        personPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
                        populasjonstilgangskontrollProvider = TillatAltPopulasjonstilgangskontrollProvider(),
                        auditlogger = Auditlogger("sp-vilkarsproving-local"),
                        transaksjonProvider = InMemoryTransaksjonProvider(),
                    ),
                // Rutene defineres i App.kt, slik at LocalApp automatisk får nye endepunkter.
                endepunkter =
                    endepunkter(
                        spleisClient = TomSpleisClient(),
                        meldingskontekst = { LoggendeMeldingskontekst },
                    ),
            )
        }

    server.start(wait = false)
    println("LocalApp startet.")
    println("OpenAPI: http://localhost:$PORT/api/openapi.json")
    println("Swagger UI: http://localhost:$PORT/api/swagger")
    Thread.currentThread().join()
}

/**
 * `configureRestRuting` legger alle rutene bak `authenticate(AZURE_AD_AUTHENTICATION_NAME)`. Lokalt
 * finnes det ingen Azure AD å snakke med, så vi registrerer en provider med samme navn som slipper
 * alle inn som en fast, oppdiktet saksbehandler.
 */
private fun Application.installerLokalAutentisering() {
    val principal =
        SaksbehandlerPrincipal(
            saksbehandler =
                Saksbehandler(
                    navIdent = NavIdent("Z999999"),
                    oid = SaksbehandlerOid(UUID.nameUUIDFromBytes("lokal-saksbehandler".toByteArray()).toString()),
                    navn = "Lokal Saksbehandler",
                ),
            tilganger = Tilgang.entries.toSet(),
            brukerroller = AppRolle.entries.toSet(),
            accessToken = AccessToken("lokalt-token"),
        )

    install(Authentication) {
        provider(AZURE_AD_AUTHENTICATION_NAME) {
            authenticate { context -> context.principal(principal) }
        }
    }
}

private class TomSpleisClient : ISpleisClient {
    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> = emptyList()
}

private object LoggendeMeldingskontekst : MessageContext {
    override fun publish(message: String) {
        println("[LocalApp] publiserer melding: $message")
    }

    override fun publish(
        key: String,
        message: String,
    ) {
        println("[LocalApp] publiserer melding på nøkkel $key: $message")
    }

    override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> {
        messages.forEachIndexed { index, melding -> println("[LocalApp] publiserer melding $index: ${melding.body}") }
        return emptyList<SentMessage>() to emptyList()
    }

    override fun rapidName() = "local"
}

private class TillatAltPopulasjonstilgangskontrollProvider : PopulasjonstilgangskontrollProvider {
    override fun kontrollerKomplettTilgang(
        accessToken: String,
        fødselsnummer: String,
    ) = TilgangskontrollResultat.Ok

    override fun kontrollerKjerneTilgang(
        accessToken: String,
        fødselsnummer: String,
    ) = TilgangskontrollResultat.Ok

    override fun kontrollerKjerneTilgangForAnsatt(
        ansattId: String,
        fødselsnummer: String,
    ) = TilgangskontrollResultat.Ok
}
