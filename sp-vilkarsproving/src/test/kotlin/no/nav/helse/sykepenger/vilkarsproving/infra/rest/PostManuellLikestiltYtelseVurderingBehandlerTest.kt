package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.AccessToken
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.NavIdent
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Saksbehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.SaksbehandlerOid
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.SaksbehandlerPrincipal
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.post
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID

class PostManuellLikestiltYtelseVurderingBehandlerTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer("12345678901")
    private val transaksjonProvider = InMemoryTransaksjonProvider()

    @Test
    fun `lagrer én vurdering per punktum og melder fra via outboxen`() =
        testApplication {
            val pseudoId = settOpp()

            val response = client.post(url(pseudoId)) { json(request(fpAap = true, yrkesaktiv = true)) }

            assertEquals(HttpStatusCode.OK, response.status)
            val id = jacksonObjectMapper().readTree(response.bodyAsText())["opptjeningsvurderingId"].asString()
            val lagret =
                transaksjonProvider.opptjeningsvurderinger.alleVurderinger.single() as Opptjeningsvurdering.VurdertISpVilkårsprøving
            assertEquals(id, lagret.id.value.toString())
            assertEquals(Vilkårskode.andreAvsnitt, lagret.vilkårsvurderinger.map { it.vilkårskode }.toSet())
            assertEquals(Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER, lagret.avgjørendeVilkårskode)
            assertEquals(
                listOf(OutboxMelding.Saksbehandlerhandling::class, OutboxMelding.OpptjeningsvurderingEndret::class),
                transaksjonProvider.outbox.upubliserteMeldinger.map { it.melding::class },
            )
        }

    @Test
    fun `ugyldig kombinasjon av svar gir 400 og lagrer ingenting`() =
        testApplication {
            val pseudoId = settOpp()

            val response = client.post(url(pseudoId)) { json(request(fpAap = null)) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
            assertEquals(emptyList<Any>(), transaksjonProvider.outbox.upubliserteMeldinger)
        }

    @Test
    fun `manglende tilgang til personen gir 403`() =
        testApplication {
            val pseudoId = settOpp(tilgangskontroll = FakeTilgangskontroll(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet)))

            val response = client.post(url(pseudoId)) { json(request()) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
        }

    @Test
    fun `kall uten Skriv-tilgang gir 403`() =
        testApplication {
            val pseudoId = settOpp(tilganger = setOf(Tilgang.Les))

            val response = client.post(url(pseudoId)) { json(request()) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
        }

    @Test
    fun `ukjent person gir 404`() =
        testApplication {
            settOpp()

            val response = client.post(url(UUID.randomUUID().toString())) { json(request()) }

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `ukjent ytelse gir 400`() =
        testApplication {
            val pseudoId = settOpp()

            val response = client.post(url(pseudoId)) { json(request().replace("FORELDREPENGER", "BARNETRYGD")) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    private fun ApplicationTestBuilder.settOpp(
        tilganger: Set<Tilgang> = setOf(Tilgang.Les, Tilgang.Skriv),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
    ): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
        application {
            settOppTestapp(SaksbehandlerPrincipal(saksbehandler, tilganger, AccessToken("token")), tilgangskontroll, pseudoIdProvider)
        }
        return pseudoId.toString()
    }

    private fun Application.settOppTestapp(
        principal: SaksbehandlerPrincipal,
        tilgangskontroll: PopulasjonstilgangskontrollProvider,
        personPseudoIdProvider: InMemoryPersonPseudoIdProvider,
    ) {
        configureContentNegotiation()
        configureResources()
        intercept(ApplicationCallPipeline.Plugins) {
            call.authentication.principal(principal)
        }
        val restAdapter =
            RestAdapter(
                personPseudoIdProvider = personPseudoIdProvider,
                populasjonstilgangskontrollProvider = tilgangskontroll,
                transaksjonProvider = transaksjonProvider,
            )
        routing {
            post(PostManuellLikestiltYtelseVurderingBehandler(), restAdapter)
        }
    }

    private class FakeTilgangskontroll(
        private val resultat: TilgangskontrollResultat = TilgangskontrollResultat.Ok,
    ) : PopulasjonstilgangskontrollProvider {
        override fun kontrollerKomplettTilgang(
            accessToken: String,
            fødselsnummer: String,
        ) = resultat

        override fun kontrollerKjerneTilgang(
            accessToken: String,
            fødselsnummer: String,
        ) = resultat

        override fun kontrollerKjerneTilgangForAnsatt(
            ansattId: String,
            fødselsnummer: String,
        ) = resultat
    }
}

@Language("JSON")
private fun request(
    fpAap: Boolean? = false,
    yrkesaktiv: Boolean? = null,
) = """
    {
      "skjæringstidspunkt": "2025-04-09",
      "sammenlagtMinst4Uker": true,
      "ytelser": ["SYKEPENGER", "FORELDREPENGER"],
      "foreldrepengerOpptjentPåAap": $fpAap,
      "yrkesaktivMinst4UkerFørUttak": $yrkesaktiv,
      "fritekstbegrunnelse": "Begrunnelse",
      "journalpostId": []
    }
    """

private fun HttpRequestBuilder.json(body: String) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

private fun url(pseudoId: String) = "/api/personer/$pseudoId/vilkarsvurderinger/manuell/likestilte-ytelser"
