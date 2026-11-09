package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Kategori
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import no.nav.helse.sykepenger.vilkarsproving.domain.avSaksbehandler
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
    fun `hopper over spleis ved oppslag når personen allerede er registrert i migreringsloggen`() {
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
    fun `avstemming kobler vurderinger til rettighetskategori-ID-ene fra spleis også når personen står i migreringsloggen`() {
        val utenKoblinger = vurdering()
        spleisSvar = listOf(utenKoblinger)
        assertEquals(1, service.avstem(FØDSELSNUMMER))
        val opprettet = Instant.parse("2026-01-02T10:15:30Z")
        spleisSvar = listOf(utenKoblinger.copy(rettighetskategorier = listOf(rettighetskategori(RETTIGHETSKATEGORI_ID, opprettet))))

        val antallNyeLagret = service.avstem(FØDSELSNUMMER)

        assertEquals(0, antallNyeLagret)
        assertEquals(2, antallSpleisKall)
        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(1, transaksjon.migreringslogg.alleNedlastinger.size)
        assertEquals(
            listOf(InMemoryOpptjeningsvurderingRepository.Kobling(utenKoblinger.opptjeningsvurderingId, RETTIGHETSKATEGORI_ID, opprettet)),
            transaksjon.opptjeningsvurderinger.koblinger,
        )
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
    fun `gjentatt avstemming lager ikke duplikater og endrer ikke koblinger som finnes`() {
        val vurdering = vurdering()
        transaksjon.opptjeningsvurderinger.lagre(vurdering.tilOpptjeningsvurdering(FØDSELSNUMMER))
        val registrertFraBehov = Instant.parse("2026-01-03T08:00:00Z")
        transaksjon.opptjeningsvurderinger.koble(vurdering.opptjeningsvurderingId, setOf(RETTIGHETSKATEGORI_ID), registrertFraBehov)
        val annenId = RettighetskategoriId(UUID.randomUUID())
        val opprettet = Instant.parse("2026-01-02T10:15:30Z")
        spleisSvar =
            listOf(
                vurdering.copy(
                    rettighetskategorier =
                        listOf(
                            rettighetskategori(RETTIGHETSKATEGORI_ID, opprettet),
                            rettighetskategori(annenId, opprettet),
                        ),
                ),
            )

        service.avstem(FØDSELSNUMMER)
        service.avstem(FØDSELSNUMMER)

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            listOf(
                InMemoryOpptjeningsvurderingRepository.Kobling(vurdering.opptjeningsvurderingId, RETTIGHETSKATEGORI_ID, registrertFraBehov),
                InMemoryOpptjeningsvurderingRepository.Kobling(vurdering.opptjeningsvurderingId, annenId, opprettet),
            ),
            transaksjon.opptjeningsvurderinger.koblinger,
        )
    }

    @Test
    fun `kobler vurderinger som importeres i samme avstemming`() {
        val opprettet = Instant.parse("2026-01-02T10:15:30Z")
        val infotrygd =
            SpleisOpptjeningsvurdering.InfotrygdArbeidstaker(
                opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID()),
                opprettet = Instant.now(),
                skjæringstidspunkt = LocalDate.of(2026, 1, 1),
                rettighetskategorier = listOf(rettighetskategori(RETTIGHETSKATEGORI_ID, opprettet)),
            )
        spleisSvar = listOf(infotrygd)

        assertEquals(1, service.lagreOpptjeningsvurderinger(FØDSELSNUMMER))

        assertEquals(
            listOf(InMemoryOpptjeningsvurderingRepository.Kobling(infotrygd.opptjeningsvurderingId, RETTIGHETSKATEGORI_ID, opprettet)),
            transaksjon.opptjeningsvurderinger.koblinger,
        )
    }

    @Test
    fun `hopper over koblinger til vurderinger som ikke er importert`() {
        spleisSvar = listOf(vurdering())
        service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)
        val kjent = spleisSvar.single() as SpleisOpptjeningsvurdering.SpleisSelvstendig
        val opprettet = Instant.parse("2026-01-02T10:15:30Z")
        val ukjent = vurdering().copy(rettighetskategorier = listOf(rettighetskategori(RettighetskategoriId(UUID.randomUUID()), opprettet)))
        spleisSvar = listOf(ukjent, kjent.copy(rettighetskategorier = listOf(rettighetskategori(RETTIGHETSKATEGORI_ID, opprettet))))

        service.avstem(FØDSELSNUMMER)

        assertEquals(1, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            listOf(InMemoryOpptjeningsvurderingRepository.Kobling(kjent.opptjeningsvurderingId, RETTIGHETSKATEGORI_ID, opprettet)),
            transaksjon.opptjeningsvurderinger.koblinger,
        )
    }

    @Test
    fun `kobler ikke vurderinger som tilhører en annen person`() {
        val spleisVurdering = vurdering()
        transaksjon.opptjeningsvurderinger.lagre(spleisVurdering.tilOpptjeningsvurdering(ET_ANNET_FØDSELSNUMMER))
        transaksjon.migreringslogg.lagre(FØDSELSNUMMER, 0, 0)
        spleisSvar = listOf(spleisVurdering.copy(rettighetskategorier = listOf(rettighetskategori(RETTIGHETSKATEGORI_ID, Instant.now()))))

        val feil =
            assertThrows<IllegalStateException> {
                service.avstem(FØDSELSNUMMER)
            }

        assertEquals(
            "Opptjeningsvurdering ${spleisVurdering.opptjeningsvurderingId} er allerede lagret for en annen person",
            feil.message,
        )
        assertEquals(emptyList<InMemoryOpptjeningsvurderingRepository.Kobling>(), transaksjon.opptjeningsvurderinger.koblinger)
    }

    @Test
    fun `kobler også vurderingen som har erstattet den backfillede, slik at behov med ID-en får den nyeste`() {
        val skjæringstidspunkt = LocalDate.of(2026, 1, 1)
        val infotrygd =
            SpleisOpptjeningsvurdering.InfotrygdArbeidstaker(
                opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID()),
                opprettet = Instant.parse("2026-01-02T10:00:00Z"),
                skjæringstidspunkt = skjæringstidspunkt,
            )
        spleisSvar = listOf(infotrygd)
        service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)
        val erstattet = transaksjon.opptjeningsvurderinger.finn(infotrygd.opptjeningsvurderingId)
        val manuell =
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = skjæringstidspunkt,
                vilkårsvurderinger =
                    listOf(
                        Vilkårsvurdering.avSaksbehandler(
                            vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                            utfall = Utfall.IkkeOppfylt,
                            saksbehandlerIdent = "Z999999",
                            fritekstbegrunnelse = "",
                        ),
                    ),
                forrigeVurdering = erstattet,
            )
        transaksjon.opptjeningsvurderinger.lagreErstatning(manuell, erstattet)
        val opprettet = Instant.parse("2026-01-02T10:15:30Z")
        spleisSvar = listOf(infotrygd.copy(rettighetskategorier = listOf(rettighetskategori(RETTIGHETSKATEGORI_ID, opprettet))))

        service.avstem(FØDSELSNUMMER)

        assertEquals(
            listOf(
                InMemoryOpptjeningsvurderingRepository.Kobling(infotrygd.opptjeningsvurderingId, RETTIGHETSKATEGORI_ID, opprettet),
                InMemoryOpptjeningsvurderingRepository.Kobling(manuell.id, RETTIGHETSKATEGORI_ID, manuell.vurdertTidspunkt),
            ),
            transaksjon.opptjeningsvurderinger.koblinger,
        )
        assertEquals(
            VurderOpptjeningResultat.HarVurdering(FØDSELSNUMMER, skjæringstidspunkt, manuell.id),
            OpptjeningService(transaksjon).vurderOpptjening(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = skjæringstidspunkt,
                kategori = Kategori.SelvstendigNæringsdrivende,
                rettighetskategoriId = RETTIGHETSKATEGORI_ID,
            ),
        )
    }

    @Test
    fun `kobler ikke nyere vurderinger med en annen kategori`() {
        val skjæringstidspunkt = LocalDate.of(2026, 1, 1)
        val infotrygd =
            SpleisOpptjeningsvurdering.InfotrygdArbeidstaker(
                opptjeningsvurderingId = OpptjeningsvurderingId(UUID.randomUUID()),
                opprettet = Instant.parse("2026-01-02T10:00:00Z"),
                skjæringstidspunkt = skjæringstidspunkt,
            )
        val selvstendig = vurdering().copy(opprettet = Instant.parse("2026-01-05T10:00:00Z"))
        spleisSvar = listOf(infotrygd, selvstendig)
        service.lagreOpptjeningsvurderinger(FØDSELSNUMMER)
        val opprettet = Instant.parse("2026-01-02T10:15:30Z")
        spleisSvar = listOf(infotrygd.copy(rettighetskategorier = listOf(rettighetskategori(RETTIGHETSKATEGORI_ID, opprettet))), selvstendig)

        service.avstem(FØDSELSNUMMER)

        assertEquals(
            listOf(InMemoryOpptjeningsvurderingRepository.Kobling(infotrygd.opptjeningsvurderingId, RETTIGHETSKATEGORI_ID, opprettet)),
            transaksjon.opptjeningsvurderinger.koblinger,
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
    fun `registrerer også tomt svar i migreringsloggen, men bare én gang`() {
        assertEquals(0, service.avstem(FØDSELSNUMMER))
        assertEquals(0, transaksjon.opptjeningsvurderinger.antallLagringer)
        assertEquals(
            InMemoryMigreringsloggRepository.Nedlasting(FØDSELSNUMMER, 0, 0),
            transaksjon.migreringslogg.alleNedlastinger.single(),
        )
        assertEquals(0, service.avstem(FØDSELSNUMMER))
        assertEquals(2, antallSpleisKall)
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

    private fun rettighetskategori(
        id: RettighetskategoriId,
        opprettet: Instant,
    ) = SpleisOpptjeningsvurdering.Rettighetskategori(id, opprettet)

    private companion object {
        val RETTIGHETSKATEGORI_ID = RettighetskategoriId(UUID.fromString("7d2f1b1e-3c4a-4f5e-9a6b-1c2d3e4f5a6b"))
        const val FØDSELSNUMMER = "12029240045"
        const val ET_ANNET_FØDSELSNUMMER = "12029240046"
    }
}
