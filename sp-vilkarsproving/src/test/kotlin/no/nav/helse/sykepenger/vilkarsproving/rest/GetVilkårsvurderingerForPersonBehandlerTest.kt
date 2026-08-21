package no.nav.helse.sykepenger.vilkarsproving.rest

import com.github.navikt.tbd_libs.populasjonstilgang.api.PopulasjonstilgangskontrollProvider
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangSomMangler
import com.github.navikt.tbd_libs.populasjonstilgang.api.TilgangskontrollResultat
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.authentication
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.helse.speil.backend.app.auditlogg.Auditlogger
import no.nav.helse.speil.backend.app.auth.AccessToken
import no.nav.helse.speil.backend.app.auth.NavIdent
import no.nav.helse.speil.backend.app.auth.Saksbehandler
import no.nav.helse.speil.backend.app.auth.SaksbehandlerOid
import no.nav.helse.speil.backend.app.auth.SaksbehandlerPrincipal
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureResources
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.get
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GetVilkårsvurderingerForPersonBehandlerTest {
    private val saksbehandler = Saksbehandler(NavIdent("Z999999"), SaksbehandlerOid("oid"), "Test Testesen")
    private val identitetsnummer = Identitetsnummer("12345678901")

    private class FakeTilgangskontroll(
        private val resultat: TilgangskontrollResultat = TilgangskontrollResultat.Ok,
    ) : PopulasjonstilgangskontrollProvider {
        var antallKall = 0

        override fun kontrollerKomplettTilgang(
            accessToken: String,
            fødselsnummer: String,
        ): TilgangskontrollResultat {
            antallKall++
            return resultat
        }

        override fun kontrollerKjerneTilgang(
            accessToken: String,
            fødselsnummer: String,
        ): TilgangskontrollResultat = resultat

        override fun kontrollerKjerneTilgangForAnsatt(
            ansattId: String,
            fødselsnummer: String,
        ): TilgangskontrollResultat = resultat
    }

    private fun principal(tilganger: Set<Tilgang> = setOf(Tilgang.Les)) = SaksbehandlerPrincipal(saksbehandler, tilganger, emptySet<AppRolle>(), AccessToken("token"))

    private fun Application.settOppTestapp(
        principal: SaksbehandlerPrincipal<AppRolle>?,
        transaksjonProvider: InMemoryTransaksjonProvider = InMemoryTransaksjonProvider(),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        personPseudoIdProvider: InMemoryPersonPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
    ) {
        configureContentNegotiation()
        configureResources()
        if (principal != null) {
            intercept(ApplicationCallPipeline.Plugins) {
                call.authentication.principal(principal)
            }
        }
        val restAdapter =
            RestAdapter<AppRolle, Transaksjonskontekst>(
                personPseudoIdProvider = personPseudoIdProvider,
                populasjonstilgangskontrollProvider = tilgangskontroll,
                auditlogger = Auditlogger("test"),
                transaksjonProvider = transaksjonProvider,
            )
        routing {
            get(GetVilkårsvurderingerForPersonBehandler(), restAdapter)
        }
    }

    private fun enVurdering(skjæringstidspunkt: LocalDate = LocalDate.of(2024, 1, 1)) =
        Vilkårsvurdering.automatisk(
            prøvingId = PrøvingId.ny(),
            fødselsnummer = identitetsnummer.value,
            skjæringstidspunkt = skjæringstidspunkt,
            grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
            vurdertTidspunkt = Instant.now(),
        )

    @Test
    fun `henter alle vurderinger for personen`() =
        testApplication {
            val transaksjonProvider = InMemoryTransaksjonProvider()
            val vurdering = enVurdering()
            transaksjonProvider.vilkårsvurderinger.lagre(vurdering)

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), transaksjonProvider, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains(vurdering.id.value.toString()))
        }

    @Test
    fun `kall uten Les-tilgang gir 403`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(tilganger = emptySet()), personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `ukjent pseudo-id gir 404, ikke 500`() =
        testApplication {
            application { settOppTestapp(principal()) }

            val response = client.get("/api/personer/${UUID.randomUUID()}/vilkarsvurderinger")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `ugyldig (ikke-UUID) person-id gir 404, ikke 500`() =
        testApplication {
            application { settOppTestapp(principal()) }

            val response = client.get("/api/personer/dette-er-ikke-en-uuid/vilkarsvurderinger")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `manglende populasjonstilgang gir 403`() =
        testApplication {
            val fake = FakeTilgangskontroll(TilgangskontrollResultat.ManglerTilgang(TilgangSomMangler.Habilitet))
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), tilgangskontroll = fake, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger")

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(1, fake.antallKall)
        }

    @Test
    fun `henter kun den ene vurderingen naar opptjeningsvurderingId er satt`() =
        testApplication {
            val transaksjonProvider = InMemoryTransaksjonProvider()
            val ønsketVurdering = enVurdering(skjæringstidspunkt = LocalDate.of(2024, 1, 1))
            val enAnnenVurdering = enVurdering(skjæringstidspunkt = LocalDate.of(2024, 6, 1))
            transaksjonProvider.vilkårsvurderinger.lagre(ønsketVurdering)
            transaksjonProvider.vilkårsvurderinger.lagre(enAnnenVurdering)

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), transaksjonProvider, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${ønsketVurdering.id.value}")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(ønsketVurdering.id.value.toString()))
            assertTrue(!body.contains(enAnnenVurdering.id.value.toString()))
        }

    @Test
    fun `ukjent opptjeningsvurderingId gir 404`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `vurdering som tilhoerer en annen person gir 404, lekker ikke andres data`() =
        testApplication {
            val enAnnenIdentitetsnummer = Identitetsnummer("98765432109")
            val transaksjonProvider = InMemoryTransaksjonProvider()
            val andresVurdering =
                Vilkårsvurdering.automatisk(
                    prøvingId = PrøvingId.ny(),
                    fødselsnummer = enAnnenIdentitetsnummer.value,
                    skjæringstidspunkt = LocalDate.of(2024, 1, 1),
                    grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
                    vurdertTidspunkt = Instant.now(),
                )
            transaksjonProvider.vilkårsvurderinger.lagre(andresVurdering)

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), transaksjonProvider, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${andresVurdering.id.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `medlemskapsvurderingId gir 500, ikke implementert ennaa`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?medlemskapsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
