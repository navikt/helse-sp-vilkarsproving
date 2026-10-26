package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.februar
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.avSaksbehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OpptjeningsvurderingResultatRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val repository = transaksjon.opptjeningsvurderinger
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingResultatRiver(
                this,
                transaksjon,
                object : ISpleisClient {
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> = error("Spleis skal ikke kalles når lokal vurdering finnes")
                },
            )
        }

    @Test
    fun `oppfylt vurdering gir ok = true`() {
        val vurdering = manuellVurdering(Utfall.Oppfylt)

        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurdering.id.value))

        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertTrue(
            løsning
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        )
    }

    @Test
    fun `ikke-oppfylt vurdering gir ok = false`() {
        val vurdering = manuellVurdering(Utfall.IkkeOppfylt)

        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurdering.id.value))

        assertEquals(1, rapid.inspektør.size)
        val løsning = rapid.inspektør.message(0)
        assertFalse(
            løsning
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        )
    }

    @Test
    fun `behov uten opptjeningsvurderingId ignoreres`() {
        @Language("JSON")
        val melding = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["OpptjeningsvurderingResultat"],
          "OpptjeningsvurderingResultat": {}
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `annet behov ignoreres`() {
        @Language("JSON")
        val melding = """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["EtHeltAnnetBehov"],
          "OpptjeningsvurderingResultat": {
            "opptjeningsvurderingId": "${UUID.randomUUID()}"
          }
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    @Test
    fun `melding med feil event_name ignoreres`() {
        @Language("JSON")
        val melding = """
        {
          "@event_name": "løsning",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["OpptjeningsvurderingResultat"],
          "OpptjeningsvurderingResultat": {
            "opptjeningsvurderingId": "${UUID.randomUUID()}"
          }
        }
        """
        rapid.sendTestMessage(melding)

        assertEquals(0, rapid.inspektør.size)
    }

    private fun manuellVurdering(utfall: Utfall): Opptjeningsvurdering.VurdertISpVilkårsprøving {
        val vilkårsvurdering =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = utfall,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "",
            )
        return Opptjeningsvurdering
            .avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                vilkårsvurderinger = listOf(vilkårsvurdering),
            ).also { repository.lagre(it) }
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"

        @Language("JSON")
        fun opptjeningsvurderingResultatBehov(opptjeningsvurderingId: UUID) =
            """
        {
          "@event_name": "behov",
          "@id": "${UUID.randomUUID()}",
          "@behov": ["OpptjeningsvurderingResultat"],
          "fødselsnummer": "01018099999",
          "OpptjeningsvurderingResultat": {
            "opptjeningsvurderingId": "$opptjeningsvurderingId"
          }
        }
        """
    }
}
