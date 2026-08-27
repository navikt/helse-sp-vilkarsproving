package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.februar
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream

internal class OpptjeningsvurderingResultatRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val repository = transaksjon.opptjeningsvurderinger
    private val rapid =
        TestRapid().apply {
            OpptjeningsvurderingResultatRiver(
                this,
                transaksjon,
                object : ISpleisClient {
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> {
                        TODO("Not yet implemented")
                    }
                },
            )
        }

    @Test
    fun `oppfylt vurdering gir ok = true`() {
        val vurdering = manuellVurdering(utfall = Utfall.Oppfylt)

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
        val vurdering = manuellVurdering(utfall = Utfall.IkkeOppfylt)

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

    @ParameterizedTest
    @MethodSource("opptjeningsVilkårskoder")
    fun `oppfylt uansett avgjørende vilkårskode gir ok = true`(vilkårskode: Vilkårskode) {
        rapid.reset()
        val vurdering = manuellVurdering(vilkårskode = vilkårskode, utfall = Utfall.Oppfylt)

        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurdering.id.value))

        val løsning = rapid.inspektør.message(0)
        assertTrue(
            løsning
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        ) {
            "$vilkårskode oppfylt skal gi ok=true"
        }
    }

    @ParameterizedTest
    @MethodSource("opptjeningsVilkårskoder")
    fun `ikke oppfylt uansett avgjørende vilkårskode gir ok = false`(vilkårskode: Vilkårskode) {
        rapid.reset()
        val vurdering = manuellVurdering(vilkårskode = vilkårskode, utfall = Utfall.IkkeOppfylt)

        rapid.sendTestMessage(opptjeningsvurderingResultatBehov(vurdering.id.value))

        val løsning = rapid.inspektør.message(0)
        assertFalse(
            løsning
                .path("@løsning")
                .path("OpptjeningsvurderingResultat")
                .path("ok")
                .asBoolean(),
        ) {
            "$vilkårskode ikke oppfylt skal gi ok=false"
        }
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

    private fun manuellVurdering(
        vilkårskode: Vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
        utfall: Utfall,
    ): Opptjeningsvurdering.VurdertISpeil {
        val ledd =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = vilkårskode,
                utfall = utfall,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "",
                vurdertTidspunkt = Instant.parse("2018-02-01T09:00:00Z"),
            )
        return Opptjeningsvurdering
            .avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            ).also { repository.lagre(it) }
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"

        @JvmStatic
        fun opptjeningsVilkårskoder(): Stream<Vilkårskode> = Vilkårskode.entries.filter { it.krav == Krav.Opptjening }.stream()

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
