package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import no.nav.helse.sykepenger.vilkarsproving.application.InMemoryTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.PersonAvstemmingService
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class PersonAvstemmingRiverTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private val opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID())
    private val rapid =
        TestRapid().apply {
            PersonAvstemmingRiver(
                rapidsConnection = this,
                personAvstemmingService =
                    PersonAvstemmingService(
                        transaksjonProvider = transaksjon,
                        spleisClient =
                            object : ISpleisClient {
                                override fun hentOpptjeningsvurderinger(fødselsnummer: String) =
                                    listOf(
                                        SpleisOpptjeningsvurdering.SpleisSelvstendig(
                                            opptjeningsvurderingId = opptjeningsvurderingId,
                                            opprettet = Instant.now(),
                                            skjæringstidspunkt = LocalDate.of(2026, 1, 1),
                                        ),
                                    )
                            },
                    ),
            )
        }

    @Test
    fun `lagrer opptjeningsvurderinger ved person-avstemming`() {
        rapid.sendTestMessage(
            """
            {
              "@event_name": "person_avstemming",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$FØDSELSNUMMER"
            }
            """.trimIndent(),
        )

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            FØDSELSNUMMER,
            transaksjon.opptjeningsvurderinger.alleVurderinger
                .single()
                .fødselsnummer,
        )
    }

    @Test
    fun `hopper over melding når opptjeningsvurderingen allerede er lagret for en annen person`() {
        sendPersonAvstemming(FØDSELSNUMMER)
        sendPersonAvstemming(ANNET_FØDSELSNUMMER)

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            FØDSELSNUMMER,
            transaksjon.opptjeningsvurderinger.alleVurderinger
                .single()
                .fødselsnummer,
        )
    }

    private fun sendPersonAvstemming(fødselsnummer: String) {
        rapid.sendTestMessage(
            """
            {
              "@event_name": "person_avstemming",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$fødselsnummer"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `ignorerer andre hendelser`() {
        rapid.sendTestMessage(
            """
            {
              "@event_name": "annen-hendelse",
              "@id": "${UUID.randomUUID()}",
              "fødselsnummer": "$FØDSELSNUMMER"
            }
            """.trimIndent(),
        )

        assertEquals(0, transaksjon.opptjeningsvurderinger.antallLagringer)
    }

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ANNET_FØDSELSNUMMER = "13029240045"
    }
}
