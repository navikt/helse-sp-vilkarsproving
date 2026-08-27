package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.openapi.OpenApiConfig
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.get
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.speil.backend.app.testfixtures.installTestPlugins
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering

private const val PORT = 8181

fun main() {
    val spleisService =
        SpleisOpptjeningsvurderingService(
            object : ISpleisClient {
                override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> = emptyList()
            },
        )
    val restAdapter =
        RestAdapter<AppRolle, Transaksjonskontekst>(
            personPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
            populasjonstilgangskontrollProvider = TillatAltPopulasjonstilgangskontrollProvider(),
            auditlogger = Auditlogger("sp-vilkarsproving-local"),
            transaksjonProvider = InMemoryTransaksjonProvider(),
        )

    val server =
        embeddedServer(CIO, port = PORT) {
            installTestPlugins(
                openApiConfig = OpenApiConfig(eksponerOpenApi = true, tittel = "sp-vilkarsproving"),
            )
            routing {
                get(GetVilkårsvurderingerForPersonBehandler(spleisService), restAdapter)
            }
        }

    server.start(wait = false)
    println("LocalApp startet.")
    println("OpenAPI: http://localhost:$PORT/api/openapi.json")
    println("Swagger UI: http://localhost:$PORT/api/swagger")
    Thread.currentThread().join()
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
