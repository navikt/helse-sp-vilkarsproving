package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.*
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val vurderinger = transaksjon.opptjeningsvurderinger
    private val prøvinger = transaksjon.opptjeningsprøvinger
    private val rapid =
        TestRapid().apply {
            GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(this, transaksjon)
        }

    @Test
    fun `løsning fullfører prøvingen og besvarer opprinnelig behov`() {
        val påbegynt = påbegyntPrøving()

        rapid.sendTestMessage(arbeidsforholdløsning(arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31")), "123")

        assertTrue(påbegynt.erAvsluttet)
        val vurdering = vurdert()
        val kilde = vurdering.vilkårsvurderinger.single().kilde as Vurderingskilde.Automatisk
        assertEquals(påbegynt.id, kilde.opptjeningsprøvingId)
        assertEquals(OPPTJENING_ARBEID_MINST_4_UKER, vurdering.avgjørendeVilkårskode)
        assertTrue(vurdering.girRettTilSykepenger)

        assertEquals(1, rapid.inspektør.size)
        val svar = rapid.inspektør.message(0)
        val partisjonsnøkkel = rapid.inspektør.key(0)
        assertEquals("123", partisjonsnøkkel)
        assertEquals(OPPRINNELIG_BEHOV_ID, svar.path("@id").asString())
        assertEquals(listOf("Opptjeningsvurdering"), svar.path("@behov").toList().map { it.asString() })
        assertEquals(
            vurdering.id.toString(),
            svar
                .path("@løsning")
                .path("Opptjeningsvurdering")
                .path("id")
                .asString(),
        )
    }

    @Test
    fun `for kort opptjening gir ikke oppfylt`() {
        påbegyntPrøving()

        rapid.sendTestMessage(arbeidsforholdløsning(arbeidsforhold(ansattSiden = "2018-01-05", ansattTil = "2018-01-31")))

        val vurdering = vurdert()
        assertEquals(OPPTJENING_ARBEID_MINST_4_UKER, vurdering.avgjørendeVilkårskode)
        assertFalse(vurdering.girRettTilSykepenger)
    }

    @Test
    fun `arbeidsforhold uten ansattTil er løpende`() {
        påbegyntPrøving()

        rapid.sendTestMessage(arbeidsforholdløsning(arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = null)))

        val arbeidsforhold = arbeidsforholdPåVurdering().single()
        assertEquals(1.januar, arbeidsforhold.ansettelseperiode.start)
        assertEquals(LocalDate.MAX, arbeidsforhold.ansettelseperiode.endInclusive)
        assertTrue(vurdert().girRettTilSykepenger)
    }

    @Test
    fun `arbeidsforhold uten orgnummer filtreres bort`() {
        påbegyntPrøving()

        rapid.sendTestMessage(
            arbeidsforholdløsning(
                arbeidsforhold(orgnummer = "", ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
                arbeidsforhold(orgnummer = ORGNUMMER, ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
            ),
        )

        assertEquals(listOf(ORGNUMMER), arbeidsforholdPåVurdering().map { it.orgnummer })
    }

    @Test
    fun `arbeidsforhold med ansattTil før ansattSiden filtreres bort`() {
        påbegyntPrøving()

        rapid.sendTestMessage(
            arbeidsforholdløsning(
                arbeidsforhold(orgnummer = "111111111", ansattSiden = "2018-01-31", ansattTil = "2018-01-01"),
                arbeidsforhold(orgnummer = ORGNUMMER, ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
            ),
        )

        assertEquals(listOf(ORGNUMMER), arbeidsforholdPåVurdering().map { it.orgnummer })
    }

    @Test
    fun `alle arbeidsforholdtyper mappes`() {
        påbegyntPrøving()

        rapid.sendTestMessage(
            arbeidsforholdløsning(
                arbeidsforhold(orgnummer = "111111111", type = "FORENKLET_OPPGJØRSORDNING", ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
                arbeidsforhold(orgnummer = "222222222", type = "FRILANSER", ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
                arbeidsforhold(orgnummer = "333333333", type = "MARITIMT", ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
                arbeidsforhold(orgnummer = "444444444", type = "ORDINÆRT", ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
            ),
        )

        assertEquals(
            listOf(
                Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING,
                Arbeidsforholdtype.FRILANSER,
                Arbeidsforholdtype.MARITIMT,
                Arbeidsforholdtype.ORDINÆRT,
            ),
            arbeidsforholdPåVurdering().map { it.type },
        )
    }

    @Test
    fun `løsning uten påbegynt prøving gir ingen melding`() {
        rapid.sendTestMessage(arbeidsforholdløsning(arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31")))

        assertEquals(0, rapid.inspektør.size)
        assertEquals(0, vurderinger.antallLagringer)
    }

    @Test
    fun `duplikat løsning gir ingen ny melding`() {
        påbegyntPrøving()
        val melding = arbeidsforholdløsning(arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31"))

        rapid.sendTestMessage(melding)
        assertEquals(1, rapid.inspektør.size)

        rapid.sendTestMessage(melding)
        assertEquals(1, rapid.inspektør.size)
        assertEquals(1, vurderinger.antallLagringer)
        assertEquals(OPPTJENING_ARBEID_MINST_4_UKER, vurdert().avgjørendeVilkårskode)
    }

    @Test
    fun `løsning som ikke er final ignoreres`() {
        val påbegynt = påbegyntPrøving()

        rapid.sendTestMessage(
            arbeidsforholdløsning(
                arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31"),
                erFinal = false,
            ),
        )

        assertEquals(0, rapid.inspektør.size)
        assertFalse(påbegynt.erAvsluttet)
    }

    @Test
    fun `ukjent arbeidsforholdtype ignoreres av valideringen`() {
        val påbegynt = påbegyntPrøving()

        rapid.sendTestMessage(
            arbeidsforholdløsning(arbeidsforhold(type = "NOE_HELT_ANNET", ansattSiden = "2018-01-01", ansattTil = "2018-01-31")),
        )

        assertEquals(0, rapid.inspektør.size)
        assertFalse(påbegynt.erAvsluttet)
    }

    @Test
    fun `løsning på allerede avsluttet prøving gir ingen melding`() {
        val (prøving, vurdering) = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)
        prøvinger.lagre(prøving)
        vurderinger.lagre(vurdering!!)

        rapid.sendTestMessage(arbeidsforholdløsning(arbeidsforhold(ansattSiden = "2018-01-01", ansattTil = "2018-01-31")))

        assertEquals(0, rapid.inspektør.size)
        val kilde = vurdert().vilkårsvurderinger.single().kilde as Vurderingskilde.Automatisk
        assertEquals(Opptjeningsgrunnlag.SelvstendigNæringsdrivende, kilde.grunnlag)
    }

    private fun påbegyntPrøving() = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving.also { prøvinger.lagre(it) }

    private fun vurdert() = vurderinger.alleVurderinger.single() as Opptjeningsvurdering.VurdertISpeil

    private fun arbeidsforholdPåVurdering() = ((vurdert().vilkårsvurderinger.single().kilde as Vurderingskilde.Automatisk).grunnlag as Opptjeningsgrunnlag.Arbeidstaker).arbeidsforhold

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ORGNUMMER = "987654321"
        val OPPRINNELIG_BEHOV_ID: String = UUID.randomUUID().toString()

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
            "@id": "$OPPRINNELIG_BEHOV_ID",
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
    }
}
