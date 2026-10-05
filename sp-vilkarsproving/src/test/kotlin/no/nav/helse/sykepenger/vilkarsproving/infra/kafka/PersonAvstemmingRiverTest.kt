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
                                            opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID()),
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
    }
}
