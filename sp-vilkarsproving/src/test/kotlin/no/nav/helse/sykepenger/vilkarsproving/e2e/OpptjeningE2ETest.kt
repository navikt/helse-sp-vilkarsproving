package no.nav.helse.sykepenger.vilkarsproving.e2e

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.vilkarsproving.application.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.db.Database
import no.nav.helse.sykepenger.vilkarsproving.infra.db.DatabaseTest
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

/**
 * E2E-tester for opptjeningsflyten gjennom alle tre rivers, mot en ekte database.
 *
 * Testene bekrefter både at meldingene går riktig vei og at tilstanden faktisk overlever lagringen:
 * radtellingene underveis viser at alt som skjer i kontekst av én melding havner i én transaksjon.
 *
 * Flyt for arbeidstaker:
 *   1. OpptjeningsvurderingRiver mottar Opptjeningsvurdering-behov → sender ArbeidsforholdV2-behov
 *   2. GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver mottar ArbeidsforholdV2-løsning → fullfører vurdering og besvarer opprinneligBehov
 *   3. OpptjeningsvurderingResultatRiver mottar OpptjeningsvurderingResultat-behov → svarer med ok=true/false
 *
 * Flyt for selvstendig næringsdrivende:
 *   1. OpptjeningsvurderingRiver mottar Opptjeningsvurdering-behov → besvarer direkte
 *   2. OpptjeningsvurderingResultatRiver mottar OpptjeningsvurderingResultat-behov → svarer med ok=true
 */
internal class OpptjeningE2ETest : DatabaseTest() {
    private val transaksjon = Database.transaksjonProvider
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingRiver(this, transaksjon)
            GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(this, transaksjon)
            OpptjeningsvurderingResultatRiver(this, transaksjon)
        }

    // === Arbeidstaker-flyt ===

    @Test
    fun `arbeidstaker med nok opptjening får ok=true`() {
        val behovId = UUID.randomUUID()

        // Steg 1: spleis ber om opptjeningsvurdering
        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)
        assertEquals(1, rapid.inspektør.size)

        val arbeidsforholdBehov = rapid.inspektør.message(0)
        assertEquals("behov", arbeidsforholdBehov.path("@event_name").asString())
        assertEquals(listOf("ArbeidsforholdV2"), arbeidsforholdBehov.path("@behov").toList().map { it.asString() })

        // Prøvingen er lagret og venter på grunnlag; ingen vurdering ennå
        assertEquals(1, Database.antallRader("vilkarsproving"))
        assertEquals(0, Database.antallRader("vilkarsvurdering"))

        // Steg 2: Aareg svarer med arbeidsforhold som gir nok opptjening (28+ dager)
        rapid.sendTestMessage(
            arbeidsforholdløsning(
                behovId = behovId,
                arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
            ),
            FØDSELSNUMMER,
        )
        assertEquals(2, rapid.inspektør.size)

        // Vurderingen og den fullførte prøvingen ble skrevet i samme transaksjon
        assertEquals(1, Database.antallRader("vilkarsproving"))
        assertEquals(1, Database.antallRader("vilkarsvurdering"))

        val opptjeningsvurderingLøsning = rapid.inspektør.message(1)
        assertEquals(behovId.toString(), opptjeningsvurderingLøsning.path("@id").asString())
        val vurderingId =
            UUID.fromString(
                opptjeningsvurderingLøsning
                    .path("@løsning")
                    .path("Opptjeningsvurdering")
                    .path("id")
                    .asString(),
            )

        // Steg 3: spleis ber om resultatet av vurderingen
        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurderingId), FØDSELSNUMMER)
        assertEquals(3, rapid.inspektør.size)

        val resultat = rapid.inspektør.message(2)
        assertTrue(
            resultat
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        )
    }

    @Test
    fun `arbeidstaker med for kort opptjening får ok=false`() {
        val behovId = UUID.randomUUID()

        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)

        // For kort: kun 27 dager (2018-01-05 til 2018-01-31 = 27 dager)
        rapid.sendTestMessage(
            arbeidsforholdløsning(
                behovId = behovId,
                arbeidsforhold(ansattSiden = "2018-01-05", ansattTil = "2018-01-31"),
            ),
            FØDSELSNUMMER,
        )

        val vurderingId =
            UUID.fromString(
                rapid.inspektør
                    .message(1)
                    .path("@løsning")
                    .path("Opptjeningsvurdering")
                    .path("id")
                    .asString(),
            )

        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurderingId), FØDSELSNUMMER)

        assertFalse(
            rapid.inspektør
                .message(2)
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        )
    }

    @Test
    fun `løpende arbeidsforhold (uten ansattTil) gir ok=true`() {
        val behovId = UUID.randomUUID()

        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)
        rapid.sendTestMessage(
            arbeidsforholdløsning(
                behovId = behovId,
                arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = null),
            ),
            FØDSELSNUMMER,
        )

        val vurderingId =
            UUID.fromString(
                rapid.inspektør
                    .message(1)
                    .path("@løsning")
                    .path("Opptjeningsvurdering")
                    .path("id")
                    .asString(),
            )
        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurderingId), FØDSELSNUMMER)

        assertTrue(
            rapid.inspektør
                .message(2)
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        )
    }

    @Test
    fun `partisjonsnøkkel bevares gjennom hele arbeidstakerflyt`() {
        val behovId = UUID.randomUUID()
        val partisjonsnøkkel = "12029240045"

        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), partisjonsnøkkel)
        assertEquals(partisjonsnøkkel, rapid.inspektør.key(0), "ArbeidsforholdV2-behov skal ha riktig partisjonsnøkkel")

        rapid.sendTestMessage(
            arbeidsforholdløsning(behovId = behovId, arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31")),
            partisjonsnøkkel,
        )
        assertEquals(partisjonsnøkkel, rapid.inspektør.key(1), "Opptjeningsvurdering-løsning skal ha riktig partisjonsnøkkel")
    }

    @Test
    fun `duplikat opptjeningsvurdering-behov sender ikke nytt ArbeidsforholdV2-behov`() {
        val behovId = UUID.randomUUID()

        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)
        assertEquals(1, rapid.inspektør.size)

        // Fullfør vurderingen
        rapid.sendTestMessage(
            arbeidsforholdløsning(behovId = behovId, arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31")),
            FØDSELSNUMMER,
        )
        assertEquals(2, rapid.inspektør.size)

        // Spleis sender behovet på nytt (replay)
        rapid.sendTestMessage(opptjeningsvurderingBehov(UUID.randomUUID(), "Arbeidstaker"), FØDSELSNUMMER)
        assertEquals(3, rapid.inspektør.size)

        // Skal svare direkte med eksisterende vurdering, ikke sende nytt ArbeidsforholdV2-behov
        val tredjeUtgang = rapid.inspektør.message(2)
        assertTrue(tredjeUtgang.hasNonNull("@løsning")) { "Skal svare med løsning, ikke nytt behov" }
        assertTrue(tredjeUtgang.path("@løsning").hasNonNull("Opptjeningsvurdering")) { "Løsningen skal inneholde Opptjeningsvurdering" }
    }

    @Test
    fun `duplikat arbeidsforholdløsning gir ikke dobbelt svar`() {
        val behovId = UUID.randomUUID()
        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "Arbeidstaker"), FØDSELSNUMMER)

        val løsning = arbeidsforholdløsning(behovId = behovId, arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31"))
        rapid.sendTestMessage(løsning, FØDSELSNUMMER)
        assertEquals(2, rapid.inspektør.size)

        rapid.sendTestMessage(løsning, FØDSELSNUMMER)
        assertEquals(2, rapid.inspektør.size) // Ingen ny melding
        assertEquals(1, Database.antallRader("vilkarsvurdering"))
    }

    // === Selvstendig næringsdrivende-flyt ===

    @Test
    fun `selvstendig næringsdrivende løses uten ArbeidsforholdV2-behov og får ok=true`() {
        val behovId = UUID.randomUUID()

        // Steg 1: spleis ber om opptjeningsvurdering
        rapid.sendTestMessage(opptjeningsvurderingBehov(behovId, "SelvstendigNæringsdrivende"), FØDSELSNUMMER)
        assertEquals(1, rapid.inspektør.size)

        val løsning = rapid.inspektør.message(0)
        assertFalse(løsning.path("@behov").any { it.asString() == "ArbeidsforholdV2" }) {
            "SelvstendigNæringsdrivende skal ikke trenge ArbeidsforholdV2"
        }
        val vurderingId =
            UUID.fromString(
                løsning
                    .path("@løsning")
                    .path("Opptjeningsvurdering")
                    .path("id")
                    .asString(),
            )

        // Steg 2: spleis ber om resultatet
        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurderingId), FØDSELSNUMMER)
        assertEquals(2, rapid.inspektør.size)

        val resultat = rapid.inspektør.message(1)
        assertTrue(
            resultat
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        )
    }

    // Vurderingen ligger i databasen, så et nytt behov besvares direkte — også etter at prosessen
    // har startet på nytt (nye rivers, ingen tilstand i minnet, samme database)
    @Test
    fun `lagret vurdering gjenbrukes av en ny instans`() {
        rapid.sendTestMessage(opptjeningsvurderingBehov(UUID.randomUUID(), "SelvstendigNæringsdrivende"), FØDSELSNUMMER)
        val vurderingId =
            rapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Opptjeningsvurdering")
                .path("id")
                .asString()

        val nyRapid =
            TestRapid().apply {
                OpptjeningsvurderingRiver(this, transaksjon)
            }
        nyRapid.sendTestMessage(opptjeningsvurderingBehov(UUID.randomUUID(), "SelvstendigNæringsdrivende"), FØDSELSNUMMER)

        assertEquals(1, nyRapid.inspektør.size)
        assertEquals(
            vurderingId,
            nyRapid.inspektør
                .message(0)
                .path("@løsning")
                .path("Opptjeningsvurdering")
                .path("id")
                .asString(),
        ) { "Eksisterende vurdering skal gjenbrukes" }
        assertEquals(1, Database.antallRader("vilkarsvurdering"))
    }

    // Feiler behandlingen av en melding etter at arbeidet er gjort, skal ingenting være lagret
    @Test
    fun `feil i behandlingen av en melding lagrer ingenting`() {
        val feilendeRapid =
            TestRapid().apply {
                OpptjeningsvurderingRiver(this, feilerEtterArbeidet)
            }

        assertThrows<RuntimeException> {
            feilendeRapid.sendTestMessage(opptjeningsvurderingBehov(UUID.randomUUID(), "SelvstendigNæringsdrivende"), FØDSELSNUMMER)
        }

        assertEquals(0, Database.antallRader("vilkarsproving"))
        assertEquals(0, Database.antallRader("vilkarsvurdering"))
    }

    /** Gjør alt arbeidet i en ekte transaksjon, men krasjer før commit. */
    private val feilerEtterArbeidet =
        object : TransaksjonProvider {
            override fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T =
                transaksjon.transaksjon { kontekst ->
                    block(kontekst)
                    throw RuntimeException("krasjer før commit")
                }
        }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ORGNUMMER = "987654321"

        @Language("JSON")
        fun opptjeningsvurderingBehov(
            id: UUID,
            arbeidssituasjon: String,
        ) = """
        {
          "@event_name": "behov",
          "@id": "$id",
          "@behov": ["Opptjeningsvurdering"],
          "fødselsnummer": "$FØDSELSNUMMER",
          "skjæringstidspunkt": "2018-02-01",
          "arbeidssituasjon": "$arbeidssituasjon"
        }
        """

        fun arbeidsforhold(
            orgnummer: String = ORGNUMMER,
            type: String = "ORDINÆRT",
            ansattSiden: String,
            ansattTil: String? = null,
        ) = """
            {
              "orgnummer": "$orgnummer",
              "type": "$type",
              "ansattSiden": "$ansattSiden",
              "ansattTil": ${ansattTil?.let { "\"$it\"" } ?: "null"}
            }
            """.trimIndent()

        @Language("JSON")
        fun arbeidsforholdløsning(
            behovId: UUID,
            vararg arbeidsforhold: String,
            erFinal: Boolean = true,
        ) = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["ArbeidsforholdV2"],
          "@final": $erFinal,
          "fødselsnummer": "$FØDSELSNUMMER",
          "skjæringstidspunkt": "2018-02-01",
          "opprinneligBehov": {
            "@event_name": "behov",
            "@id": "$behovId",
            "@behov": ["Opptjeningsvurdering"],
            "fødselsnummer": "$FØDSELSNUMMER",
            "skjæringstidspunkt": "2018-02-01",
            "arbeidssituasjon": "Arbeidstaker"
          },
          "@løsning": {
            "ArbeidsforholdV2": [${arbeidsforhold.joinToString()}]
          }
        }
        """

        @Language("JSON")
        fun opptjeningsvurderingResultatBehov(opptjeningsvurderingId: UUID) =
            """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["OpptjeningsvurderingResultat"],
          "OpptjeningsvurderingResultat": {
            "opptjeningsvurderingId": "$opptjeningsvurderingId"
          }
        }
        """
    }
}
