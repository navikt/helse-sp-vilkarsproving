package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Grunnlagsbehov
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsprøving
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

internal class PostgresVilkårsprøvingRepositoryTest : DatabaseTest() {
    // Prøvingen skal komme tilbake fra databasen med den tilstanden den hadde, slik at en ny pod
    // kan ta over midt i flyten
    @Test
    fun `prøving som venter på grunnlag lagres og hentes tilbake`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.vilkårsprøvinger.lagre(prøving) }

        val lagret = transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!

        assertEquals(prøving.id, lagret.id)
        assertEquals(Vilkår.Opptjening, lagret.vilkår)
        assertEquals(FØDSELSNUMMER, lagret.fødselsnummer)
        assertEquals(1.februar, lagret.skjæringstidspunkt)
        assertEquals(prøving.startet.truncatedTo(ChronoUnit.MICROS), lagret.startet.truncatedTo(ChronoUnit.MICROS))
        assertEquals(Grunnlagsbehov.Arbeidsforhold, lagret.uteståendeBehov)
        assertFalse(lagret.erAvsluttet)
    }

    @Test
    fun `prøving i tilstand startet lagres og hentes tilbake`() {
        val prøving = prøvingFraLagring(Vilkårsprøving.Tilstand.Startet)
        transaksjon { it.vilkårsprøvinger.lagre(prøving) }

        val lagret = transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!

        assertEquals(Vilkårsprøving.Tilstand.Startet, lagret.tilstand)
        assertNull(lagret.uteståendeBehov)
        assertFalse(lagret.erAvsluttet)
    }

    // En prøving som fullføres uten innhenting skrives med vurderingen sin med en gang
    @Test
    fun `fullført prøving lagres med vurderingen`() {
        val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)
        transaksjon {
            it.vilkårsprøvinger.lagre(påbegynt.prøving)
            it.vilkårsvurderinger.lagre(påbegynt.vurdering!!)
        }

        val lagret = transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!

        assertTrue(lagret.erAvsluttet)
        assertEquals(Vilkårsprøving.Tilstand.Fullført(påbegynt.vurdering!!.id), lagret.tilstand)
    }

    // Samme metode brukes for å skrive en endret prøving: raden oppdateres, det blir ikke en ny
    @Test
    fun `lagring av en endret prøving oppdaterer raden`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.vilkårsprøvinger.lagre(prøving) }

        val vurdering =
            transaksjon { kontekst ->
                val vurdering = prøving.motta(arbeidstakergrunnlag())
                kontekst.vilkårsvurderinger.lagre(vurdering)
                kontekst.vilkårsprøvinger.lagre(prøving)
                vurdering
            }

        val lagret = transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!
        assertEquals(1, Database.antallRader("vilkarsproving"))
        assertEquals(Vilkårsprøving.Tilstand.Fullført(vurdering.id), lagret.tilstand)
        assertNull(lagret.uteståendeBehov)
    }

    // Feltene som ligger fast fra prøvingen startet skal ikke skrives om ved en oppdatering
    @Test
    fun `gjentatt lagring beholder starttidspunktet`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.vilkårsprøvinger.lagre(prøving) }
        transaksjon { it.vilkårsprøvinger.lagre(prøving) }

        val lagret = transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!
        assertEquals(1, Database.antallRader("vilkarsproving"))
        assertEquals(prøving.startet.truncatedTo(ChronoUnit.MICROS), lagret.startet.truncatedTo(ChronoUnit.MICROS))
    }

    // Invarianten håndheves av det partielle unike indekset, ikke av en sjekk i applikasjonskoden.
    // `on conflict (id)` fanger bare opp den samme prøvingen på nytt, ikke en ny prøving på samme nøkkel.
    @Test
    fun `tabellen nekter to aktive prøvinger på samme nøkkel`() {
        transaksjon { it.vilkårsprøvinger.lagre(nyPrøving()) }

        assertThrows<PSQLException> { transaksjon { it.vilkårsprøvinger.lagre(nyPrøving()) } }

        assertEquals(1, Database.antallRader("vilkarsproving"))
    }

    // ... men en fullført prøving er ikke aktiv, så en ny prøving kan startes etterpå
    @Test
    fun `ny prøving kan startes når den forrige er fullført`() {
        val første = nyPrøving()
        transaksjon { kontekst ->
            kontekst.vilkårsprøvinger.lagre(første)
            kontekst.vilkårsvurderinger.lagre(første.motta(arbeidstakergrunnlag()))
            kontekst.vilkårsprøvinger.lagre(første)
        }

        val andre = nyPrøving()
        transaksjon { it.vilkårsprøvinger.lagre(andre) }

        assertEquals(2, Database.antallRader("vilkarsproving"))
        assertEquals(andre.id, transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!.id)
    }

    @Test
    fun `aktive prøvinger på ulike nøkler er tillatt`() {
        transaksjon { kontekst ->
            kontekst.vilkårsprøvinger.lagre(nyPrøving(skjæringstidspunkt = 1.februar))
            kontekst.vilkårsprøvinger.lagre(nyPrøving(skjæringstidspunkt = 1.januar))
            kontekst.vilkårsprøvinger.lagre(nyPrøving(fødselsnummer = ANNET_FØDSELSNUMMER))
        }

        assertEquals(3, Database.antallRader("vilkarsproving"))
    }

    @Test
    fun `finnSiste gir null når det ikke finnes noen prøving`() {
        assertNull(transaksjon { it.vilkårsprøvinger.finnSiste(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) })
    }

    private fun nyPrøving(
        fødselsnummer: String = FØDSELSNUMMER,
        skjæringstidspunkt: LocalDate = 1.februar,
    ) = Opptjeningsprøving.start(fødselsnummer, skjæringstidspunkt, Arbeidssituasjon.Arbeidstaker).prøving

    private fun prøvingFraLagring(tilstand: Vilkårsprøving.Tilstand) =
        Vilkårsprøving.fraLagring(
            id = PrøvingId.ny(),
            vilkår = Vilkår.Opptjening,
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
            startet = Instant.now(),
            tilstand = tilstand,
        )

    private companion object {
        const val ANNET_FØDSELSNUMMER = "12029240046"
    }
}
