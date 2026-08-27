package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Grunnlagsbehov
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.infra.db.FØDSELSNUMMER
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidstakergrunnlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class PostgresOpptjeningsprøvingRepositoryTest : DatabaseTest() {
    // Prøvingen skal komme tilbake fra databasen med den tilstanden den hadde, slik at en ny pod
    // kan ta over midt i flyten
    @Test
    fun `prøving som venter på grunnlag lagres og hentes tilbake`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.opptjeningsprøvinger.lagre(prøving) }

        val lagret = transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }!!

        assertEquals(prøving.id, lagret.id)
        assertEquals(FØDSELSNUMMER, lagret.fødselsnummer)
        assertEquals(1.februar, lagret.skjæringstidspunkt)
        assertEquals(prøving.startet.truncatedTo(ChronoUnit.MILLIS), lagret.startet.truncatedTo(ChronoUnit.MILLIS))
        assertEquals(Grunnlagsbehov.Arbeidsforhold, lagret.uteståendeBehov)
        assertFalse(lagret.erAvsluttet)
    }

    @Test
    fun `prøving i tilstand startet lagres og hentes tilbake`() {
        val prøving = prøvingFraLagring(Opptjeningsprøving.Tilstand.Startet)
        transaksjon { it.opptjeningsprøvinger.lagre(prøving) }

        val lagret = transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }!!

        assertEquals(Opptjeningsprøving.Tilstand.Startet, lagret.tilstand)
        assertNull(lagret.uteståendeBehov)
        assertFalse(lagret.erAvsluttet)
    }

    // En prøving som fullføres uten innhenting skrives med vurderingen sin med en gang
    @Test
    fun `fullført prøving lagres med vurderingen`() {
        val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)
        transaksjon {
            it.opptjeningsprøvinger.lagre(påbegynt.prøving)
            it.kravvurderinger.lagre(påbegynt.vurdering!!)
        }

        val lagret = transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }!!

        assertTrue(lagret.erAvsluttet)
        assertEquals(Opptjeningsprøving.Tilstand.Fullført(påbegynt.vurdering!!.id), lagret.tilstand)
    }

    // Samme metode brukes for å skrive en endret prøving: raden oppdateres, det blir ikke en ny
    @Test
    fun `lagring av en endret prøving oppdaterer raden`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.opptjeningsprøvinger.lagre(prøving) }

        val vurdering =
            transaksjon { kontekst ->
                val vurdering = prøving.motta(arbeidstakergrunnlag())
                kontekst.kravvurderinger.lagre(vurdering)
                kontekst.opptjeningsprøvinger.lagre(prøving)
                vurdering
            }

        val lagret = transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }!!
        assertEquals(1, Database.antallRader("kravproving"))
        assertEquals(Opptjeningsprøving.Tilstand.Fullført(vurdering.id), lagret.tilstand)
        assertNull(lagret.uteståendeBehov)
    }

    // Feltene som ligger fast fra prøvingen startet skal ikke skrives om ved en oppdatering
    @Test
    fun `gjentatt lagring beholder starttidspunktet`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.opptjeningsprøvinger.lagre(prøving) }
        transaksjon { it.opptjeningsprøvinger.lagre(prøving) }

        val lagret = transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }!!
        assertEquals(1, Database.antallRader("kravproving"))
        assertEquals(prøving.startet.truncatedTo(ChronoUnit.MILLIS), lagret.startet.truncatedTo(ChronoUnit.MILLIS))
    }

    // Invarianten håndheves av det partielle unike indekset, ikke av en sjekk i applikasjonskoden.
    // `on conflict (id)` fanger bare opp den samme prøvingen på nytt, ikke en ny prøving på samme nøkkel.
    @Test
    fun `tabellen nekter to aktive prøvinger på samme nøkkel`() {
        transaksjon { it.opptjeningsprøvinger.lagre(nyPrøving()) }

        assertThrows<PSQLException> { transaksjon { it.opptjeningsprøvinger.lagre(nyPrøving()) } }

        assertEquals(1, Database.antallRader("kravproving"))
    }

    // ... men en fullført prøving er ikke aktiv, så en ny prøving kan startes etterpå
    @Test
    fun `ny prøving kan startes når den forrige er fullført`() {
        val første = nyPrøving()
        transaksjon { kontekst ->
            kontekst.opptjeningsprøvinger.lagre(første)
            kontekst.kravvurderinger.lagre(første.motta(arbeidstakergrunnlag()))
            kontekst.opptjeningsprøvinger.lagre(første)
        }

        val andre = nyPrøving()
        transaksjon { it.opptjeningsprøvinger.lagre(andre) }

        assertEquals(2, Database.antallRader("kravproving"))
        assertEquals(andre.id, transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }!!.id)
    }

    @Test
    fun `aktive prøvinger på ulike nøkler er tillatt`() {
        transaksjon { kontekst ->
            kontekst.opptjeningsprøvinger.lagre(nyPrøving(skjæringstidspunkt = 1.februar))
            kontekst.opptjeningsprøvinger.lagre(nyPrøving(skjæringstidspunkt = 1.januar))
            kontekst.opptjeningsprøvinger.lagre(nyPrøving(fødselsnummer = ANNET_FØDSELSNUMMER))
        }

        assertEquals(3, Database.antallRader("kravproving"))
    }

    @Test
    fun `finnSiste gir null når det ikke finnes noen prøving`() {
        assertNull(transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) })
    }

    private fun nyPrøving(
        fødselsnummer: String = FØDSELSNUMMER,
        skjæringstidspunkt: LocalDate = 1.februar,
    ) = Opptjeningsprøving.start(fødselsnummer, skjæringstidspunkt, Arbeidssituasjon.Arbeidstaker).prøving

    private fun prøvingFraLagring(tilstand: Opptjeningsprøving.Tilstand) =
        Opptjeningsprøving.fraLagring(
            id = OpptjeningsprøvingId.ny(),
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
            startet = Instant.now(),
            tilstand = tilstand,
        )

    private companion object {
        const val ANNET_FØDSELSNUMMER = "12029240046"
    }
}
