package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.*
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureContentNegotiation
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins.configureResources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestAdapter
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.post
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.testfixtures.InMemoryPersonPseudoIdProvider
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

class PostManuellOpptjeningsvurderingBehandlerTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer("12345678901")
    private val transaksjonProvider = InMemoryTransaksjonProvider()

    @Test
    fun `bare arbeid lagrer første ledd oppfylt og melder fra via outboxen`() =
        testApplication {
            val pseudoId = settOpp()

            val response = client.post(url(pseudoId)) { json(request(opptjeningsaktiviteter = """["ARBEID"]""")) }

            assertEquals(HttpStatusCode.OK, response.status)
            val id = jacksonObjectMapper().readTree(response.bodyAsText())["opptjeningsvurderingId"].asString()
            val lagret = lagret()
            assertEquals(id, lagret.id.value.toString())
            assertEquals(
                listOf(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER),
                lagret.vilkårsvurderinger.map { it.vilkårskode })
            assertTrue(lagret.erOk)
            assertEquals(
                listOf(OutboxMelding.Saksbehandlerhandling::class, OutboxMelding.OpptjeningsvurderingEndret::class),
                transaksjonProvider.outbox.upubliserteMeldinger.map { it.melding::class },
            )
        }

    @Test
    fun `likestilt ytelse lagrer første ledd og én vurdering per punktum i andre ledd`() =
        testApplication {
            val pseudoId = settOpp()

            val response =
                client.post(url(pseudoId)) {
                    json(
                        request(
                            opptjeningsaktiviteter = """["ARBEID", "FORELDREPENGER"]""",
                            fpAap = true,
                            yrkesaktiv = true
                        )
                    )
                }

            assertEquals(HttpStatusCode.OK, response.status)
            val lagret = lagret()
            assertEquals(
                Vilkårskode.entries.toSet(),
                lagret.vilkårsvurderinger.map { it.vilkårskode }.toSet(),
            )
            assertTrue(lagret.erOk)
        }

    @Test
    fun `ikke minst 4 uker gir 400 inntil videre`() =
        testApplication {
            val pseudoId = settOpp()

            val response =
                client.post(url(pseudoId)) { json(request(minst4Uker = false, opptjeningsaktiviteter = "[]")) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
        }

    @Test
    fun `foreldrepenger opptjent på AAP uten yrkesaktivitet før uttak gir 400 inntil videre`() =
        testApplication {
            val pseudoId = settOpp()

            val response =
                client.post(url(pseudoId)) {
                    json(request(opptjeningsaktiviteter = """["ARBEID", "FORELDREPENGER"]""", fpAap = true, yrkesaktiv = false))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
        }

    @Test
    fun `likestilt ytelse gir 400 når det ikke er skrudd på`() =
        testApplication {
            val pseudoId = settOpp(likestilteYtelserSkruddPå = false)

            val response =
                client.post(url(pseudoId)) { json(request(opptjeningsaktiviteter = """["ARBEID", "SYKEPENGER"]""")) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
        }

    @Test
    fun `bare arbeid er lov selv om likestilte ytelser ikke er skrudd på`() =
        testApplication {
            val pseudoId = settOpp(likestilteYtelserSkruddPå = false)

            val response = client.post(url(pseudoId)) { json(request(opptjeningsaktiviteter = """["ARBEID"]""")) }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `ugyldig kombinasjon av svar gir 400 og lagrer ingenting`() =
        testApplication {
            val pseudoId = settOpp()

            val response =
                client.post(url(pseudoId)) { json(request(opptjeningsaktiviteter = """["FORELDREPENGER"]""")) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(0, transaksjonProvider.opptjeningsvurderinger.antallLagringer)
            assertEquals(emptyList<Any>(), transaksjonProvider.outbox.upubliserteMeldinger)
        }

    @Test
    fun `manglende tilgang til personen gir 403`() =
        testApplication {
            val pseudoId = settOpp(
                tilgangskontroll = FakeTilgangskontroll(
                    TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet)
                )
            )

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
    fun `ukjent opptjeningsaktivitet gir 400`() =
        testApplication {
            val pseudoId = settOpp()

            val response = client.post(url(pseudoId)) { json(request(opptjeningsaktiviteter = """["BARNETRYGD"]""")) }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    private fun lagret() =
        transaksjonProvider.opptjeningsvurderinger.alleVurderinger.single() as Opptjeningsvurdering.VurdertISpVilkårsprøving

    private fun ApplicationTestBuilder.settOpp(
        tilganger: Set<Tilgang> = setOf(Tilgang.Les, Tilgang.Skriv),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        likestilteYtelserSkruddPå: Boolean = true,
    ): String {
        val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
        val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)
        application {
            configureContentNegotiation()
            configureResources()
            val principal = SaksbehandlerPrincipal(saksbehandler, tilganger, AccessToken("token"))
            intercept(ApplicationCallPipeline.Plugins) {
                call.authentication.principal(principal)
            }
            val restAdapter =
                RestAdapter(
                    personPseudoIdProvider = pseudoIdProvider,
                    populasjonstilgangskontrollProvider = tilgangskontroll,
                    transaksjonProvider = transaksjonProvider,
                )
            routing {
                post(PostManuellOpptjeningsvurderingBehandler(likestilteYtelserSkruddPå), restAdapter)
            }
        }
        return pseudoId.toString()
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
    minst4Uker: Boolean = true,
    opptjeningsaktiviteter: String = """["ARBEID"]""",
    fpAap: Boolean? = null,
    yrkesaktiv: Boolean? = null,
) = """
    {
      "skjæringstidspunkt": "2025-04-09",
      "minst4Uker": $minst4Uker,
      "opptjeningsaktiviteter": $opptjeningsaktiviteter,
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

private fun url(pseudoId: String) = "/api/personer/$pseudoId/vilkarsvurderinger/manuell/opptjening"
