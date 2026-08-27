package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.februar
import no.nav.helse.hendelser.somPeriode
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.util.*

class OpptjeningsvurderingResultatRiverCallthroughTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val vurderingIdSpleisArbeidstaker = UUID.randomUUID()
    private val vurderingIdSpleisArbeidstakerIkkeOppfylt = UUID.randomUUID()
    private val vurderingIdSpleisSelvstendig = UUID.randomUUID()
    private val vurderingIdSpleisInfotrygd = UUID.randomUUID()

    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingResultatRiver(
                this,
                transaksjon,
                object : ISpleisClient {
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> =
                        when (fødselsnummer) {
                            FØDSELSNUMMER ->
                                listOf(
                                    SpleisOpptjeningsvurdering.SpleisArbeidstaker(
                                        opptjeningsvurderingId = OpptjeningsvurderingId(vurderingIdSpleisArbeidstaker),
                                        skjæringstidspunkt = 1.februar,
                                        oppfylt = true,
                                        antallDager = 31,
                                        opptjeningsperiode = januar,
                                        arbeidsforhold = listOf(),
                                    ),
                                    SpleisOpptjeningsvurdering.SpleisArbeidstaker(
                                        opptjeningsvurderingId = OpptjeningsvurderingId(vurderingIdSpleisArbeidstakerIkkeOppfylt),
                                        skjæringstidspunkt = 1.februar,
                                        oppfylt = false,
                                        antallDager = 1,
                                        opptjeningsperiode = 31.januar.somPeriode(),
                                        arbeidsforhold = listOf(),
                                    ),
                                    SpleisOpptjeningsvurdering.SpleisSelvstendig(
                                        opptjeningsvurderingId = OpptjeningsvurderingId(vurderingIdSpleisSelvstendig),
                                        skjæringstidspunkt = 1.februar,
                                    ),
                                    SpleisOpptjeningsvurdering.InfotrygdArbeidstaker(
                                        opptjeningsvurderingId = OpptjeningsvurderingId(vurderingIdSpleisInfotrygd),
                                        skjæringstidspunkt = 1.februar,
                                    ),
                                )
                            else -> emptyList()
                        }
                },
            )
        }

    private fun JsonNode.ok() =
        this
            .path("@løsning")
            .path("OpptjeningsvurderingResultat")
            .path("ok")
            .asBoolean()

    @Test
    fun `ikke oppfylt hos Spleis gir ok = false`() {
        rapid.sendTestMessage(
            opptjeningsvurderingResultatBehov(fnr = FØDSELSNUMMER, opptjeningsvurderingId = vurderingIdSpleisArbeidstakerIkkeOppfylt),
        )
        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertFalse(løsning.ok())
    }

    @Test
    fun `oppfylt for arbeidstaker hos Spleis gir ok = true`() {
        rapid.sendTestMessage(
            opptjeningsvurderingResultatBehov(fnr = FØDSELSNUMMER, opptjeningsvurderingId = vurderingIdSpleisArbeidstaker),
        )
        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertTrue(løsning.ok())
    }

    @Test
    fun `selvstendig hos Spleis gir ok = true`() {
        rapid.sendTestMessage(
            opptjeningsvurderingResultatBehov(fnr = FØDSELSNUMMER, opptjeningsvurderingId = vurderingIdSpleisSelvstendig),
        )
        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertTrue(løsning.ok())
    }

    @Test
    fun `infotrygd hos Spleis gir ok = true`() {
        rapid.sendTestMessage(
            opptjeningsvurderingResultatBehov(fnr = FØDSELSNUMMER, opptjeningsvurderingId = vurderingIdSpleisInfotrygd),
        )
        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertTrue(løsning.ok())
    }

    @Test
    fun `tom liste for FNR fra Spleis gir ikke svar`() {
        rapid.sendTestMessage(
            opptjeningsvurderingResultatBehov(fnr = "ukjent", opptjeningsvurderingId = UUID.randomUUID()),
        )
        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `ukjent opptjeningsvurderingId for Spleis gir ikke svar`() {
        rapid.sendTestMessage(
            opptjeningsvurderingResultatBehov(fnr = FØDSELSNUMMER, opptjeningsvurderingId = UUID.randomUUID()),
        )
        assertEquals(0, rapid.inspektør.size)
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"

        @Language("JSON")
        fun opptjeningsvurderingResultatBehov(
            fnr: String = "01018099999",
            opptjeningsvurderingId: UUID,
        ) = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["OpptjeningsvurderingResultat"],
          "fødselsnummer": "$fnr",
          "OpptjeningsvurderingResultat": {
            "opptjeningsvurderingId": "$opptjeningsvurderingId"
          }
        }
        """
    }
}
