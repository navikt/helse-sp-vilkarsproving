package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilOpptjeningsvurdering
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class PersonAvstemmingServiceTest {
    private val transaksjon = InMemoryTransaksjonProvider()
    private var spleisSvar = emptyList<SpleisOpptjeningsvurdering>()
    private var spleisFeil: RuntimeException? = null
    private var antallSpleisKall = 0
    private val service =
        PersonAvstemmingService(
            transaksjonProvider = transaksjon,
            spleisClient =
                object : ISpleisClient {
                    override fun hentOpptjeningsvurderinger(fødselsnummer: String): List<SpleisOpptjeningsvurdering> {
                        antallSpleisKall++
                        spleisFeil?.let { throw it }
                        return spleisSvar
                    }
                },
        )

    @Test
    fun `lagrer alle vurderingene fra spleis og returnerer antallet`() {
        spleisSvar = listOf(vurdering(), vurdering())

        val antallLagret = service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)

        assertEquals(2, antallLagret)
        assertEquals(2, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            setOf(FØDSELSNUMMER),
            transaksjon.opptjeningsvurderinger.alleVurderinger
                .map { it.fødselsnummer }
                .toSet(),
        )
        assertEquals(
            InMemoryMigreringsloggRepository.Nedlasting(
                fødselsnummer = FØDSELSNUMMER,
                antallVurderinger = 2,
                antallHoppetOver = 0,
            ),
            transaksjon.migreringslogg.alleNedlastinger.single(),
        )
    }

    @Test
    fun `hopper over spleis når personen allerede er registrert i migreringsloggen`() {
        spleisSvar = listOf(vurdering())
        assertEquals(1, service.lagreOpptjeningsvurderinger(FØDSELSNUMMER))
        spleisFeil = IllegalStateException("Spleis skal ikke kalles igjen")

        val antallNyeLagret = service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)

        assertEquals(0, antallNyeLagret)
        assertEquals(1, antallSpleisKall)
        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(1, transaksjon.migreringslogg.alleNedlastinger.size)
    }

    @Test
    fun `teller vurderinger som var lagret før migreringen`() {
        val tidligereVurdering = vurdering()
        transaksjon.opptjeningsvurderinger.lagre(tidligereVurdering.tilOpptjeningsvurdering(FØDSELSNUMMER))
        spleisSvar = listOf(tidligereVurdering, vurdering())

        assertEquals(1, service.lagreOpptjeningsvurderinger(FØDSELSNUMMER))
        assertEquals(
            InMemoryMigreringsloggRepository.Nedlasting(
                fødselsnummer = FØDSELSNUMMER,
                antallVurderinger = 2,
                antallHoppetOver = 1,
            ),
            transaksjon.migreringslogg.alleNedlastinger.single(),
        )
    }

    @Test
    fun `feiler hvis vurderings-ID allerede tilhører en annen person`() {
        val spleisVurdering = vurdering()
        transaksjon.opptjeningsvurderinger.lagre(
            Opptjeningsvurdering.fraInfotrygd(
                id = spleisVurdering.opptjeningsvurderingId,
                vurdertTidspunkt = spleisVurdering.opprettet,
                fødselsnummer = ET_ANNET_FØDSELSNUMMER,
                skjæringstidspunkt = spleisVurdering.skjæringstidspunkt,
                erOk = true,
            ),
        )
        spleisSvar = listOf(spleisVurdering)

        val feil =
            assertThrows<IllegalStateException> {
                service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)
            }

        assertEquals(
            "Opptjeningsvurdering ${spleisVurdering.opptjeningsvurderingId} er allerede lagret for en annen person",
            feil.message,
        )
        assertEquals(
            ET_ANNET_FØDSELSNUMMER,
            transaksjon.opptjeningsvurderinger.alleVurderinger
                .single()
                .fødselsnummer,
        )
    }

    @Test
    fun `registrerer også tomt svar slik at spleis ikke kalles igjen`() {
        assertEquals(0, service.lagreOpptjeningsvurderinger(FØDSELSNUMMER))
        assertEquals(0, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            InMemoryMigreringsloggRepository.Nedlasting(FØDSELSNUMMER, 0, 0),
            transaksjon.migreringslogg.alleNedlastinger.single(),
        )
        assertEquals(0, service.lagreOpptjeningsvurderinger(FØDSELSNUMMER))
        assertEquals(1, antallSpleisKall)
        assertEquals(1, transaksjon.migreringslogg.alleNedlastinger.size)
    }

    @Test
    fun `videresender feil fra spleis`() {
        val forventetFeil = IllegalStateException("Spleis er utilgjengelig")
        spleisFeil = forventetFeil

        val feil =
            assertThrows<IllegalStateException> {
                service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)
            }

        assertEquals(forventetFeil, feil)
        assertEquals(0, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(0, transaksjon.migreringslogg.alleNedlastinger.size)
    }

    private fun vurdering() =
        SpleisOpptjeningsvurdering.SpleisSelvstendig(
            opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID()),
            opprettet = Instant.now(),
            skjæringstidspunkt = LocalDate.of(2026, 1, 1),
        )

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
        const val ET_ANNET_FØDSELSNUMMER = "12029240046"
    }
}
