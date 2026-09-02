package no.nav.helse.sykepenger.vilkarsproving.infra.rest

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
import no.nav.helse.speil.backend.app.auth.*
import no.nav.helse.speil.backend.app.person.Identitetsnummer
import no.nav.helse.speil.backend.app.plugins.configureContentNegotiation
import no.nav.helse.speil.backend.app.plugins.configureResources
import no.nav.helse.speil.backend.app.rest.RestAdapter
import no.nav.helse.speil.backend.app.rest.get
import no.nav.helse.speil.backend.app.testfixtures.InMemoryPersonPseudoIdProvider
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClientException
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.*

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
        ): TilgangskontrollResultat = resultat

        override fun kontrollerKjerneTilgang(
            accessToken: String,
            fødselsnummer: String,
        ): TilgangskontrollResultat {
            antallKall++
            return resultat
        }

        override fun kontrollerKjerneTilgangForAnsatt(
            ansattId: String,
            fødselsnummer: String,
        ): TilgangskontrollResultat = resultat
    }

    private fun principal(tilganger: Set<Tilgang> = setOf(Tilgang.Les)) = SaksbehandlerPrincipal(saksbehandler, tilganger, emptySet<AppRolle>(), AccessToken("token"))

    private class FakeSpleisClient(
        private val vurderinger: List<SpleisOpptjeningsvurdering> = emptyList(),
        private val svikt: RuntimeException? = null,
    ) : ISpleisClient {
        override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> {
            svikt?.let { throw it }
            return vurderinger
        }
    }

    private fun Application.settOppTestapp(
        principal: SaksbehandlerPrincipal<AppRolle>?,
        transaksjonProvider: InMemoryTransaksjonProvider = InMemoryTransaksjonProvider(),
        tilgangskontroll: PopulasjonstilgangskontrollProvider = FakeTilgangskontroll(),
        personPseudoIdProvider: InMemoryPersonPseudoIdProvider = InMemoryPersonPseudoIdProvider(),
        spleisClient: ISpleisClient = FakeSpleisClient(),
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
            get(GetVilkårsvurderingerForPersonBehandler(SpleisOpptjeningsvurderingService(spleisClient)), restAdapter)
        }
    }

    @Test
    fun `kall uten Les-tilgang gir 403`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(tilganger = emptySet()), personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `ukjent pseudo-id gir 404, ikke 500`() =
        testApplication {
            application { settOppTestapp(principal()) }

            val response = client.get("/api/personer/${UUID.randomUUID()}/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `ugyldig (ikke-UUID) person-id gir 404, ikke 500`() =
        testApplication {
            application { settOppTestapp(principal()) }

            val response = client.get("/api/personer/dette-er-ikke-en-uuid/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

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

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(1, fake.antallKall)
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
                Opptjeningsvurdering.automatisk(
                    opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                    fødselsnummer = enAnnenIdentitetsnummer.value,
                    skjæringstidspunkt = LocalDate.of(2024, 1, 1),
                    grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
                    vurdertTidspunkt = Instant.now(),
                )
            transaksjonProvider.opptjeningsvurderinger.lagre(andresVurdering)

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), transaksjonProvider, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${andresVurdering.id.value}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    /**
     * Formen på json-en er selve kontrakten mot Speil, og den produseres av Jackson — ikke av
     * kotlinx-annotasjonene, som kun styrer openapi-spec-en. Testen går derfor helt ut på wire, og
     * ville ikke fanget en manglende `@JsonTypeInfo` om den bare sammenlignet kotlin-objekter.
     */
    @Test
    fun `responsen har diskriminator paa kilde og grunnlag`() =
        testApplication {
            val transaksjonProvider = InMemoryTransaksjonProvider()
            val vurdering =
                Opptjeningsvurdering.automatisk(
                    opptjeningsprøvingId = OpptjeningsprøvingId.ny(),
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = LocalDate.of(2024, 2, 1),
                    grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende,
                    vurdertTidspunkt = Instant.parse("2024-02-01T12:00:00Z"),
                )
            transaksjonProvider.opptjeningsvurderinger.lagre(vurdering)

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), transaksjonProvider, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${vurdering.id.value}")
            assertEquals(HttpStatusCode.OK, response.status)

            val json = jacksonObjectMapper().readTree(response.bodyAsText())

            assertEquals("2024-02-01", json["skjæringstidspunkt"].asString())

            val krav = json["krav"].single()
            assertEquals("OPPTJENING", krav["kravkode"].asString())
            assertEquals(true, krav["opptjeningOk"].asBoolean())
            assertEquals("VURDERT_I_SPEIL", krav["kravkilde"].asString())
            assertEquals("OPPTJENING_ARBEID_MINST_4_UKER", krav["avgjørendeVilkårskode"].asString())

            val vilkårsvurdering = krav["vurderinger"].single()
            assertEquals("OPPTJENING_ARBEID_MINST_4_UKER", vilkårsvurdering["vilkårskode"].asString())
            assertEquals("OPPFYLT", vilkårsvurdering["utfall"].asString())

            val kilde = vilkårsvurdering["kilde"]
            assertEquals("AUTOMATISK", kilde["kildetype"].asString()) { "Uten diskriminator kan Speil ikke se hvilken kilde den fikk: $kilde" }
            assertEquals("SELVSTENDIG_NAERINGSDRIVENDE", kilde["grunnlag"]["grunnlagstype"].asString())

            // Diskriminatoren er både en deklarert property og en Jackson-annotasjon. Skrives den to
            // ganger, er json-en i praksis ødelagt for strengt validerende konsumenter.
            assertEquals(1, Regex("\"kildetype\"").findAll(response.bodyAsText()).count())
        }

    /**
     * Infotrygd-kravet har verken sti eller avgjørende vilkår. Feltene skal da være helt fraværende
     * i json-en, ikke stå der som null eller tom liste — en konsument skal ikke måtte gjette om en
     * tom sti betyr «overtatt fra Infotrygd» eller «noe er galt».
     */
    @Test
    fun `infotrygdkrav sendes uten sti og uten avgjoerende vilkår`() =
        testApplication {
            val transaksjonProvider = InMemoryTransaksjonProvider()
            val vurdering =
                Opptjeningsvurdering.fraInfotrygd(
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = LocalDate.of(2024, 2, 1),
                    erOk = true,
                )
            transaksjonProvider.opptjeningsvurderinger.lagre(vurdering)

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), transaksjonProvider, personPseudoIdProvider = pseudoIdProvider)
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${vurdering.id.value}")
            assertEquals(HttpStatusCode.OK, response.status)

            val krav = jacksonObjectMapper().readTree(response.bodyAsText())["krav"].single()

            assertEquals("OVERFOERT_FRA_INFOTRYGD", krav["kravkilde"].asString())
            assertEquals(true, krav["opptjeningOk"].asBoolean())
            assertFalse(krav.has("vurderinger")) { "Infotrygd-kravet skal ikke ha en sti: $krav" }
            assertFalse(krav.has("avgjørendeVilkårskode")) { "Vi kjenner ikke det avgjørende vilkåret: $krav" }
        }

    /**
     * Vurderingen finnes ikke i vår database — typisk fordi den ble gjort automatisk i spleis før
     * dette systemet begynte å lagre vurderinger selv. Da skal vi falle tilbake til spleis-api.
     */
    @Test
    fun `finner ikke vurdering i db, men finner den hos spleis som arbeidstaker`() =
        testApplication {
            val opptjeningsvurderingId = UUID.randomUUID()
            val spleisVurdering =
                SpleisOpptjeningsvurdering.SpleisArbeidstaker(
                    opptjeningsvurderingId = OpptjeningsvurderingId(opptjeningsvurderingId),
                    skjæringstidspunkt = LocalDate.of(2024, 3, 1),
                    oppfylt = true,
                    antallDager = 30,
                    opptjeningsperiode = null,
                    arbeidsforhold =
                        listOf(
                            SpleisOpptjeningsvurdering.SpleisArbeidstaker.Arbeidsforhold(
                                organisasjonsnummer = "123456789",
                                ansettelsesperioder =
                                    listOf(
                                        SpleisOpptjeningsvurdering.SpleisArbeidstaker.Ansettelsesperiode(
                                            fom = LocalDate.of(2024, 1, 1),
                                            tom = null,
                                        ),
                                    ),
                            ),
                        ),
                )

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(
                    principal(),
                    personPseudoIdProvider = pseudoIdProvider,
                    spleisClient = FakeSpleisClient(vurderinger = listOf(spleisVurdering)),
                )
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$opptjeningsvurderingId")
            assertEquals(HttpStatusCode.OK, response.status)

            val krav = jacksonObjectMapper().readTree(response.bodyAsText())["krav"].single()
            assertEquals("VURDERT_I_SPEIL", krav["kravkilde"].asString())
            assertEquals(true, krav["opptjeningOk"].asBoolean())

            val kilde = krav["vurderinger"].single()["kilde"]
            assertEquals("OVERFOERT_FRA_SPLEIS", kilde["kildetype"].asString())
        }

    @Test
    fun `finner ikke vurdering i db, men finner den hos spleis som selvstendig`() =
        testApplication {
            val opptjeningsvurderingId = UUID.randomUUID()
            val spleisVurdering =
                SpleisOpptjeningsvurdering.SpleisSelvstendig(
                    opptjeningsvurderingId = OpptjeningsvurderingId(opptjeningsvurderingId),
                    skjæringstidspunkt = LocalDate.of(2024, 3, 1),
                )

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(
                    principal(),
                    personPseudoIdProvider = pseudoIdProvider,
                    spleisClient = FakeSpleisClient(vurderinger = listOf(spleisVurdering)),
                )
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$opptjeningsvurderingId")
            assertEquals(HttpStatusCode.OK, response.status)

            val krav = jacksonObjectMapper().readTree(response.bodyAsText())["krav"].single()
            assertEquals(true, krav["opptjeningOk"].asBoolean())
        }

    @Test
    fun `finner ikke vurdering i db, men finner den hos spleis som overfoert fra infotrygd`() =
        testApplication {
            val opptjeningsvurderingId = UUID.randomUUID()
            val spleisVurdering =
                SpleisOpptjeningsvurdering.InfotrygdArbeidstaker(
                    opptjeningsvurderingId = OpptjeningsvurderingId(opptjeningsvurderingId),
                    skjæringstidspunkt = LocalDate.of(2024, 3, 1),
                )

            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(
                    principal(),
                    personPseudoIdProvider = pseudoIdProvider,
                    spleisClient = FakeSpleisClient(vurderinger = listOf(spleisVurdering)),
                )
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=$opptjeningsvurderingId")
            assertEquals(HttpStatusCode.OK, response.status)

            val krav = jacksonObjectMapper().readTree(response.bodyAsText())["krav"].single()
            assertEquals("OVERFOERT_FRA_INFOTRYGD", krav["kravkilde"].asString())
            assertEquals(true, krav["opptjeningOk"].asBoolean())
        }

    @Test
    fun `finnes verken i db eller hos spleis gir 404`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(principal(), personPseudoIdProvider = pseudoIdProvider, spleisClient = FakeSpleisClient())
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun `spleis-api svikter gir 503, ikke 500`() =
        testApplication {
            val pseudoIdProvider = InMemoryPersonPseudoIdProvider()
            val pseudoId = pseudoIdProvider.nyPersonPseudoId(identitetsnummer)

            application {
                settOppTestapp(
                    principal(),
                    personPseudoIdProvider = pseudoIdProvider,
                    spleisClient = FakeSpleisClient(svikt = SpleisClientException("spleis-api svarte 500")),
                )
            }

            val response = client.get("/api/personer/$pseudoId/vilkarsvurderinger?opptjeningsvurderingId=${UUID.randomUUID()}")

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        }
}
