package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.*
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.sykepenger.vilkarsproving.infra.rettighetskategori.RettighetskategoriWiremock
import no.nav.helse.til
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.*

internal class OpptjeningsvurderingRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val vurderinger = transaksjon.opptjeningsvurderinger
    private val prøvinger = transaksjon.opptjeningsprøvinger
    private val rettighetskategori = RettighetskategoriWiremock()
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingRiver(this, transaksjon, rettighetskategori.client)
        }

    @AfterEach
    fun tearDown() {
        rettighetskategori.stopp()
    }

    // For arbeidstakere må vi hente arbeidsforhold før vi kan vurdere, så riveren
    // sender ut et nytt behov i stedet for en løsning
    @Test
    fun `arbeidstaker gir behov om arbeidsforhold`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("ARBEIDSTAKER")), "123")

        assertEquals(1, rapid.inspektør.size)
        val utgående = rapid.inspektør.message(0)
        val partisjonsnøkkelForUtgåendeMelding = rapid.inspektør.key(0)
        assertEquals("123", partisjonsnøkkelForUtgåendeMelding)
        assertEquals("behov", utgående.path("@event_name").asString())
        assertEquals(listOf("ArbeidsforholdV2"), utgående.path("@behov").toList().map { it.asString() })
        assertEquals(FØDSELSNUMMER, utgående.path("fødselsnummer").asString())
        assertEquals("2018-02-01", utgående.path("skjæringstidspunkt").asString())
        assertTrue(utgående.hasNonNull("opprinneligBehov"))
    }

    // Prøvingen er prosessen som venter på grunnlaget; ingen vurdering finnes ennå
    @Test
    fun `arbeidstaker starter en prøving uten å produsere en vurdering`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("ARBEIDSTAKER")))

        val prøving = prøvinger.allePrøvinger.single()
        assertFalse(prøving.erAvsluttet)
        assertEquals(Grunnlagsbehov.Arbeidsforhold, prøving.uteståendeBehov)
        assertEquals(0, vurderinger.antallLagringer)
    }

    // Selvstendig næringsdrivende kan vurderes med en gang, og løsningen legges på
    // det innkommende behovet
    @Test
    fun `selvstendig næringsdrivende løses umiddelbart`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")))

        assertEquals(1, rapid.inspektør.size)
        val vurdering = vurderinger.finn(OpptjeningsvurderingId(UUID.fromString(løsningsId(0)))) as Opptjeningsvurdering.VurdertISpVilkårsprøving
        assertTrue(vurdering.erOk)
        assertEquals(Kategori.SelvstendigNæringsdrivende, vurdering.kategori)
        assertTrue(prøvinger.allePrøvinger.single().erAvsluttet)
    }

    @Test
    fun `hovedkategorien avgjør kategorien, ikke arbeidssituasjon`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE"), arbeidssituasjon = "Arbeidstaker"))

        val vurdering = vurderinger.finn(OpptjeningsvurderingId(UUID.fromString(løsningsId(0))))!!
        assertEquals(Kategori.SelvstendigNæringsdrivende, vurdering.kategori)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Frilans", "Arbeidsledig", "Ukjent"])
    fun `arbeidssituasjon leses ikke`(arbeidssituasjon: String) {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE"), arbeidssituasjon = arbeidssituasjon))

        assertEquals(1, rapid.inspektør.size)
        assertTrue(rapid.inspektør.message(0).path("@løsning").path("Opptjeningsvurdering").hasNonNull("id"))
    }

    @Test
    fun `behov uten arbeidssituasjon besvares`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE"), arbeidssituasjon = null))

        assertEquals(1, rapid.inspektør.size)
        assertTrue(rapid.inspektør.message(0).path("@løsning").path("Opptjeningsvurdering").hasNonNull("id"))
    }

    // Har vi allerede en vurdering for samme kategori svarer vi med den eksisterende id-en
    // i stedet for å be om arbeidsforhold på nytt
    @Test
    fun `eksisterende vurdering for samme kategori gjenbrukes for ukjent rettighetskategoriId`() {
        val eksisterende = fullførtArbeidstakerprøving()

        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("ARBEIDSTAKER")))

        assertEquals(1, rapid.inspektør.size)
        assertEquals(eksisterende.toString(), løsningsId(0))
        assertEquals(1, rettighetskategori.loggedRequests().size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["saksbehandler", "infotrygd", "spleis"])
    fun `gjenbruk på samme kategori virker uansett kilde`(kilde: String) {
        val eksisterende =
            when (kilde) {
                "saksbehandler" -> manuellVurdering()
                "infotrygd" -> Opptjeningsvurdering.fraInfotrygd(fødselsnummer = FØDSELSNUMMER, skjæringstidspunkt = 1.februar, erOk = true, vurdertTidspunkt = Instant.now())
                else -> {
                    val automatisk = vurderinger.finn(fullførtArbeidstakerprøving()) as Opptjeningsvurdering.VurdertISpVilkårsprøving
                    Opptjeningsvurdering.spleisFraLagring(OpptjeningsvurderingId.ny(), FØDSELSNUMMER, 1.februar, automatisk.vilkårsvurderinger, true, Kategori.Arbeidstaker)
                }
            }.also { vurderinger.lagre(it) }

        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("ARBEIDSTAKER")))

        assertEquals(eksisterende.id.toString(), løsningsId(0))
    }

    @Test
    fun `endret hovedkategori fra arbeidstaker til selvstendig gir ny vurdering`() {
        val arbeidstaker = fullførtArbeidstakerprøving()

        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")))

        assertNotEquals(arbeidstaker.toString(), løsningsId(0))
        assertEquals(2, vurderinger.antallLagringer)
    }

    @Test
    fun `endret hovedkategori fra selvstendig til arbeidstaker starter ny prøving`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")))

        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("ARBEIDSTAKER")))

        assertEquals(listOf("ArbeidsforholdV2"), rapid.inspektør.message(1).path("@behov").toList().map { it.asString() })
        assertEquals(2, prøvinger.allePrøvinger.size)
    }

    @Test
    fun `ny ID med bare en ny tilleggskategori gir samme vurdering`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")))
        val første = løsningsId(0)

        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE", tilleggskategorier = listOf("JORDBRUKER"))))

        assertEquals(første, løsningsId(1))
        assertEquals(1, vurderinger.antallLagringer)
    }

    @Test
    fun `kjent rettighetskategoriId svarer med koblet vurdering uten oppslag`() {
        val rettighetskategoriId = stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")
        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))
        val koblet = løsningsId(0)

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertEquals(2, rapid.inspektør.size)
        assertEquals(koblet, løsningsId(1))
        assertEquals(1, prøvinger.allePrøvinger.size)
        assertEquals(1, rettighetskategori.loggedRequests().size)
    }

    @Test
    fun `kjent rettighetskategoriId svarer selv om sp-rettighetskategori er nede`() {
        val rettighetskategoriId = stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")
        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))
        rettighetskategori.stubFeil(rettighetskategoriId, 503)

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertEquals(løsningsId(0), løsningsId(1))
    }

    @Test
    fun `kjent rettighetskategoriId svarer med nyeste koblede vurdering`() {
        val rettighetskategoriId = stubbetRettighetskategori("ARBEIDSTAKER")
        val eldste = fullførtArbeidstakerprøving()
        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))
        assertEquals(eldste.toString(), løsningsId(0))
        val nyeste = fullførtArbeidstakerprøving()
        vurderinger.koble(nyeste, setOf(RettighetskategoriId(rettighetskategoriId)), Instant.now())

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertEquals(nyeste.toString(), løsningsId(1))
    }

    @Test
    fun `gjentatt behov med samme rettighetskategoriId lagrer koblingen én gang`() {
        fullførtArbeidstakerprøving()
        val rettighetskategoriId = stubbetRettighetskategori("ARBEIDSTAKER")

        repeat(2) { rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId)) }

        assertEquals(1, vurderinger.koblinger.size)
    }

    @Test
    fun `ny vurdering kobles til rettighetskategoriId`() {
        val rettighetskategoriId = stubbetRettighetskategori("SELVSTENDIG_NAERINGSDRIVENDE")
        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))
        val ny = løsningsId(0)
        // En nyere vurdering for samme skjæringstidspunkt uten kobling skal ikke vinne over den koblede
        fullførtArbeidstakerprøving()

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertEquals(ny, løsningsId(1))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ARBEIDSTAKER_OG_SELVSTENDIG_NAERINGSDRIVENDE", "FRILANSER"])
    fun `hovedkategori opptjening ikke kan vurdere ignoreres`(hovedkategori: String) {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori(hovedkategori)))

        assertIngenBehandling()
    }

    @Test
    fun `manglende hovedkategori ignoreres`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(stubbetRettighetskategori(null)))

        assertIngenBehandling()
    }

    @Test
    fun `ingen løsning når sp-rettighetskategori svarer 5xx tre ganger`() {
        val rettighetskategoriId = UUID.randomUUID()
        rettighetskategori.stubFeil(rettighetskategoriId, 500)

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertIngenBehandling()
        assertEquals(3, rettighetskategori.loggedRequests().size)
    }

    @Test
    fun `ingen løsning og ingen nye forsøk når sp-rettighetskategori svarer 4xx`() {
        val rettighetskategoriId = UUID.randomUUID()
        rettighetskategori.stubFeil(rettighetskategoriId, 404)

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertIngenBehandling()
        assertEquals(1, rettighetskategori.loggedRequests().size)
    }

    @Test
    fun `opprinneligBehov er json-objekt med identisk innhold som innkommende behov`() {
        val innkommendeBehov = opptjeningsvurderingBehov(stubbetRettighetskategori("ARBEIDSTAKER"))
        rapid.sendTestMessage(innkommendeBehov)

        val utgående = rapid.inspektør.message(0)
        val opprinneligBehov = utgående.path("opprinneligBehov")

        assertInstanceOf(
            ObjectNode::class.java,
            opprinneligBehov,
        ) { "opprinneligBehov skal være et JSON-objekt, ikke en string" }

        val forventet = jacksonObjectMapper().readTree(innkommendeBehov)
        // Rapids beriker meldingen med metadata-felter — vi sjekker at alle opprinnelige felter er bevart
        forventet.properties().forEach { (key, value) ->
            assertEquals(value, opprinneligBehov.get(key)) { "Feltet '$key' i opprinneligBehov stemmer ikke" }
        }
    }

    @Test
    fun `arbeidstaker med rettighetskategoriId beholder feltet i opprinneligBehov`() {
        val rettighetskategoriId = stubbetRettighetskategori("ARBEIDSTAKER")
        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId))

        assertEquals(1, rapid.inspektør.size)
        val utgående = rapid.inspektør.message(0)
        assertEquals(listOf("ArbeidsforholdV2"), utgående.path("@behov").toList().map { it.asString() })
        assertEquals(
            rettighetskategoriId.toString(),
            utgående
                .path("opprinneligBehov")
                .path("Opptjeningsvurdering")
                .path("rettighetskategoriId")
                .asString(),
        )
    }

    @Test
    fun `behov uten rettighetskategoriId ignoreres`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId = null))

        assertIngenBehandling()
        assertTrue(rettighetskategori.loggedRequests().isEmpty())
    }

    @Test
    fun `behov med null som rettighetskategoriId ignoreres`() {
        val behov = jacksonObjectMapper().readTree(opptjeningsvurderingBehov(UUID.randomUUID()))
        (behov.path("Opptjeningsvurdering") as ObjectNode).putNull("rettighetskategoriId")

        rapid.sendTestMessage(behov.toString())

        assertIngenBehandling()
    }

    @Test
    fun `behov uten rettighetskategoriId besvares ikke selv om vurderingen finnes`() {
        fullførtArbeidstakerprøving()

        rapid.sendTestMessage(opptjeningsvurderingBehov(rettighetskategoriId = null))

        assertEquals(0, rapid.inspektør.size)
    }

    // Riveren skal ikke plukke opp behov som allerede har fått en løsning
    @Test
    fun `behov med løsning ignoreres`() {
        @Language("JSON")
        val melding = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["Opptjeningsvurdering"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "Opptjeningsvurdering": { "skjæringstidspunkt": "2018-02-01", "rettighetskategoriId": "${UUID.randomUUID()}" },
          "@løsning": { "Opptjeningsvurdering": { "id": "${UUID.randomUUID()}" } }
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `andre behov ignoreres`() {
        @Language("JSON")
        val melding = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["EtHeltAnnetBehov"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "Opptjeningsvurdering": { "skjæringstidspunkt": "2018-02-01", "rettighetskategoriId": "${UUID.randomUUID()}" }
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    // Mangler påkrevde felter skal meldingen ikke behandles
    @Test
    fun `behov uten skjæringstidspunkt ignoreres`() {
        @Language("JSON")
        val melding = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["Opptjeningsvurdering"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "Opptjeningsvurdering" : {
            "rettighetskategoriId": "${UUID.randomUUID()}"
          }
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    private fun stubbetRettighetskategori(
        hovedkategori: String?,
        tilleggskategorier: List<String> = emptyList(),
        rettighetskategoriId: UUID = UUID.randomUUID(),
    ): UUID {
        rettighetskategori.stubRettighetskategori(rettighetskategoriId, FØDSELSNUMMER, hovedkategori, tilleggskategorier)
        return rettighetskategoriId
    }

    private fun assertIngenBehandling() {
        assertEquals(0, rapid.inspektør.size)
        assertTrue(prøvinger.allePrøvinger.isEmpty())
        assertEquals(0, vurderinger.antallLagringer)
    }

    private fun løsningsId(indeks: Int) =
        rapid.inspektør
            .message(indeks)
            .path("@løsning")
            .path("Opptjeningsvurdering")
            .path("id")
            .asString()

    private fun fullførtArbeidstakerprøving(): OpptjeningsvurderingId {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        val arbeidsforhold =
            Arbeidsforhold(orgnummer = ORGNUMMER, ansettelseperiode = 1.januar til 31.januar, type = ORDINÆRT)
        val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(listOf(arbeidsforhold)))
        prøvinger.lagre(prøving)
        vurderinger.lagre(vurdering)
        return vurdering.id
    }

    private fun manuellVurdering() =
        Opptjeningsvurdering.avSaksbehandler(
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
            vilkårsvurderinger =
                listOf(
                    Vilkårsvurdering.avSaksbehandler(
                        vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                        utfall = Utfall.Oppfylt,
                        saksbehandlerIdent = "Z999999",
                        fritekstbegrunnelse = "",
                    ),
                ),
        )

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ORGNUMMER = "987654321"

        fun opptjeningsvurderingBehov(
            rettighetskategoriId: UUID?,
            arbeidssituasjon: String? = null,
        ): String {
            val felter =
                listOfNotNull(
                    """"skjæringstidspunkt": "2018-02-01"""",
                    arbeidssituasjon?.let { """"arbeidssituasjon": "$it"""" },
                    rettighetskategoriId?.let { """"rettighetskategoriId": "$it"""" },
                ).joinToString()

            @Language("JSON")
            val behov = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["Opptjeningsvurdering"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "Opptjeningsvurdering" : { $felter }
        }
        """
            return behov
        }
    }
}
