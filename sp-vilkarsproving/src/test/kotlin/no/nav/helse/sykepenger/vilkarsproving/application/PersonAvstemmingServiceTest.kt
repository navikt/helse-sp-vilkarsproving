package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisOpptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.tilOpptjeningsvurdering
import no.nav.helse.til
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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

    @ParameterizedTest
    @CsvSource(
        "'', UKJENT_PRIVAT",
        "'   ', UKJENT_PRIVAT",
        "987654321, 987654321",
    )
    fun `lagrer arbeidsforhold fra spleis med UKJENT_PRIVAT bare når organisasjonsnummer er blankt`(
        organisasjonsnummer: String,
        forventetOrgnummer: String,
    ) {
        val ansattFom = LocalDate.of(2025, 12, 1)
        val skjæringstidspunkt = LocalDate.of(2026, 1, 1)
        spleisSvar =
            listOf(
                SpleisOpptjeningsvurdering.SpleisArbeidstaker(
                    opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID()),
                    opprettet = Instant.now(),
                    skjæringstidspunkt = skjæringstidspunkt,
                    oppfylt = true,
                    antallDager = 31,
                    opptjeningsperiode = ansattFom til skjæringstidspunkt.minusDays(1),
                    arbeidsforhold =
                        listOf(
                            SpleisOpptjeningsvurdering.SpleisArbeidstaker.Arbeidsforhold(
                                organisasjonsnummer = organisasjonsnummer,
                                ansettelsesperioder =
                                    listOf(
                                        SpleisOpptjeningsvurdering.SpleisArbeidstaker.Ansettelsesperiode(ansattFom, null),
                                    ),
                            ),
                        ),
                ),
            )

        assertEquals(1, service.lagreOpptjeningsvurderinger(FØDSELSNUMMER))
        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        val lagret = transaksjon.opptjeningsvurderinger.alleVurderinger.single() as Opptjeningsvurdering.OverførtFraSpleis
        val kilde = lagret.vilkårsvurderinger.single().kilde as Vurderingskilde.OverførtFraSpleis
        val grunnlag = kilde.grunnlag as Opptjeningsgrunnlag.Arbeidstaker
        assertEquals(
            listOf(
                Arbeidsforhold(
                    orgnummer = forventetOrgnummer,
                    ansattFom = ansattFom,
                    ansattTom = null,
                    type = Arbeidsforhold.Arbeidsforholdtype.UKJENT,
                ),
            ),
            grunnlag.arbeidsforhold,
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
