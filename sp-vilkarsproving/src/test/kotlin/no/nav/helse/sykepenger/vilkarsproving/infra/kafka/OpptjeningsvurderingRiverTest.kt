package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.*
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.til
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

internal class OpptjeningsvurderingRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val vurderinger = transaksjon.opptjeningsvurderinger
    private val prøvinger = transaksjon.opptjeningsprøvinger
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingRiver(this, transaksjon)
        }

    // For arbeidstakere må vi hente arbeidsforhold før vi kan vurdere, så riveren
    // sender ut et nytt behov i stedet for en løsning
    @Test
    fun `arbeidstaker gir behov om arbeidsforhold`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon = "Arbeidstaker"), "123")

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
        rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon = "Arbeidstaker"))

        val prøving = prøvinger.allePrøvinger.single()
        assertFalse(prøving.erAvsluttet)
        assertEquals(Grunnlagsbehov.Arbeidsforhold, prøving.uteståendeBehov)
        assertEquals(0, vurderinger.antallLagringer)
    }

    // Selvstendig næringsdrivende kan vurderes med en gang, og løsningen legges på
    // det innkommende behovet
    @Test
    fun `selvstendig næringsdrivende løses umiddelbart`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon = "SelvstendigNæringsdrivende"))

        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        val opptjeningsvurderingId =
            OpptjeningsvurderingId(
                UUID.fromString(
                    løsning
                        .path("@løsning")
                        .path("Opptjeningsvurdering")
                        .path("id")
                        .asString(),
                ),
            )

        val vurdering = vurderinger.finn(opptjeningsvurderingId) as Opptjeningsvurdering.VurdertISpVilkårsprøving
        assertTrue(vurdering.erOk)
        assertTrue(prøvinger.allePrøvinger.single().erAvsluttet)
    }

    // Har vi allerede en vurdering svarer vi med den eksisterende id-en
    // i stedet for å be om arbeidsforhold på nytt
    @Test
    fun `eksisterende vurdering svares ut direkte`() {
        val eksisterende = fullførtPrøving()

        rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon = "Arbeidstaker"))

        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertEquals(
            eksisterende.toString(),
            løsning
                .path("@løsning")
                .path("Opptjeningsvurdering")
                .path("id")
                .asString(),
        )
    }

    @Test
    fun `opprinneligBehov er json-objekt med identisk innhold som innkommende behov`() {
        val innkommendeBehov = opptjeningsvurderingBehov(arbeidssituasjon = "Arbeidstaker")
        rapid.sendTestMessage(innkommendeBehov)

        val utgående = rapid.inspektør.message(0)
        val opprinneligBehov = utgående.path("opprinneligBehov")

        assertInstanceOf(
            ObjectNode::class.java,
            opprinneligBehov,
        ) { "opprinneligBehov skal være et JSON-objekt, ikke en string" }

        val objectMapper =
            jacksonObjectMapper()
        val forventet = objectMapper.readTree(innkommendeBehov)
        // Rapids beriker meldingen med metadata-felter — vi sjekker at alle opprinnelige felter er bevart
        forventet.properties().forEach { (key, value) ->
            assertEquals(value, opprinneligBehov.get(key)) { "Feltet '$key' i opprinneligBehov stemmer ikke" }
        }
    }

    @Test
    fun `selvstendig næringsdrivende med rettighetskategoriId løses som før`() {
        rapid.sendTestMessage(
            opptjeningsvurderingBehov(arbeidssituasjon = "SelvstendigNæringsdrivende", rettighetskategoriId = UUID.randomUUID()),
        )

        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertTrue(løsning.path("@løsning").path("Opptjeningsvurdering").hasNonNull("id"))
    }

    @Test
    fun `arbeidstaker med rettighetskategoriId beholder feltet i opprinneligBehov`() {
        val rettighetskategoriId = UUID.randomUUID()
        rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon = "Arbeidstaker", rettighetskategoriId = rettighetskategoriId))

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

    @ParameterizedTest
    @ValueSource(strings = ["Arbeidstaker", "SelvstendigNæringsdrivende"])
    fun `behov uten rettighetskategoriId ignoreres`(arbeidssituasjon: String) {
        rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon, rettighetskategoriId = null))

        assertEquals(0, rapid.inspektør.size)
        assertTrue(prøvinger.allePrøvinger.isEmpty())
        assertEquals(0, vurderinger.antallLagringer)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Arbeidstaker", "SelvstendigNæringsdrivende"])
    fun `behov med null som rettighetskategoriId ignoreres`(arbeidssituasjon: String) {
        val behov = jacksonObjectMapper().readTree(opptjeningsvurderingBehov(arbeidssituasjon))
        (behov.path("Opptjeningsvurdering") as ObjectNode).putNull("rettighetskategoriId")

        rapid.sendTestMessage(behov.toString())

        assertEquals(0, rapid.inspektør.size)
        assertTrue(prøvinger.allePrøvinger.isEmpty())
        assertEquals(0, vurderinger.antallLagringer)
    }

    @Test
    fun `behov uten rettighetskategoriId besvares ikke selv om vurderingen finnes`() {
        fullførtPrøving()

        rapid.sendTestMessage(opptjeningsvurderingBehov("Arbeidstaker", rettighetskategoriId = null))

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
          "skjæringstidspunkt": "2018-02-01",
          "arbeidssituasjon": "Arbeidstaker",
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
          "skjæringstidspunkt": "2018-02-01",
          "arbeidssituasjon": "Arbeidstaker"
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
            "arbeidssituasjon": "Arbeidstaker"
          }
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `behov med ukjent arbeidssituasjon feiler`() {
        assertThrows<IllegalArgumentException> {
            rapid.sendTestMessage(opptjeningsvurderingBehov(arbeidssituasjon = "Ukjent"))
        }

        assertEquals(0, rapid.inspektør.size)
        assertEquals(0, prøvinger.allePrøvinger.size)
    }

    private fun fullførtPrøving(): OpptjeningsvurderingId {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        val arbeidsforhold =
            Arbeidsforhold(orgnummer = ORGNUMMER, ansettelseperiode = 1.januar til 31.januar, type = ORDINÆRT)
        val vurdering = prøving.motta(Opptjeningsgrunnlag.Arbeidstaker(listOf(arbeidsforhold)))
        prøvinger.lagre(prøving)
        vurderinger.lagre(vurdering)
        return vurdering.id
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ORGNUMMER = "987654321"

        fun opptjeningsvurderingBehov(
            arbeidssituasjon: String,
            rettighetskategoriId: UUID? = UUID.randomUUID(),
        ): String {
            val rettighetskategorifelt = rettighetskategoriId?.let { ""","rettighetskategoriId": "$it"""" } ?: ""

            @Language("JSON")
            val behov = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["Opptjeningsvurdering"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "Opptjeningsvurdering" : {
              "skjæringstidspunkt": "2018-02-01",
              "arbeidssituasjon": "$arbeidssituasjon"$rettighetskategorifelt
          }
        }
        """
            return behov
        }
    }
}
