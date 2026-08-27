package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.februar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.infra.db.FØDSELSNUMMER
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidstakergrunnlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Transaksjonsgrensen er meldingsgrensen: alt arbeidet som gjøres for én melding skal enten bli
 * lagret i sin helhet, eller ikke i det hele tatt. Et halvferdig resultat — en vurdering uten
 * prøving, eller en prøving som er avsluttet uten at vurderingen finnes — skal være umulig.
 */
internal class PostgresTransaksjonProviderTest : DatabaseTest() {
    @Test
    fun `alt arbeidet i transaksjonen lagres når blokken går gjennom`() {
        transaksjon { kontekst ->
            val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)
            kontekst.opptjeningsprøvinger.lagre(påbegynt.prøving)
            kontekst.kravvurderinger.lagre(checkNotNull(påbegynt.vurdering))
        }

        assertEquals(1, Database.antallRader("kravproving"))
        assertEquals(1, Database.antallRader("kravvurdering"))
        assertEquals(1, Database.antallRader("vilkarsvurdering"))
    }

    @Test
    fun `ingenting lagres når noe feiler underveis`() {
        assertThrows<RuntimeException> {
            transaksjon { kontekst ->
                val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)
                kontekst.opptjeningsprøvinger.lagre(påbegynt.prøving)
                kontekst.kravvurderinger.lagre(checkNotNull(påbegynt.vurdering))
                throw RuntimeException("noe gikk galt etter at arbeidet var gjort")
            }
        }

        assertEquals(0, Database.antallRader("kravproving"))
        assertEquals(0, Database.antallRader("kravvurdering"))
        assertEquals(0, Database.antallRader("vilkarsvurdering"))
    }

    // Feiler vi etter at vurderingen er lagret, men før prøvingen er oppdatert, skal begge deler
    // forsvinne. Ellers ville prøvingen ligget igjen som aktiv med en vurdering som ikke finnes.
    @Test
    fun `delvis fullført prøving rulles tilbake i sin helhet`() {
        val prøving = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.Arbeidstaker).prøving
        transaksjon { it.opptjeningsprøvinger.lagre(prøving) }

        assertThrows<RuntimeException> {
            transaksjon { kontekst ->
                kontekst.kravvurderinger.lagre(prøving.motta(arbeidstakergrunnlag()))
                kontekst.opptjeningsprøvinger.lagre(prøving)
                throw RuntimeException("krasjer før commit")
            }
        }

        assertEquals(0, Database.antallRader("kravvurdering"))
        assertEquals(0, Database.antallRader("vilkarsvurdering"))
        val lagret = transaksjon { it.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar) }
        assertNotNull(lagret)
        assertEquals(false, lagret!!.erAvsluttet) { "Prøvingen skal fortsatt vente på grunnlag" }
    }

    @Test
    fun `skriving er ikke synlig utenfra før transaksjonen er commitet`() {
        transaksjon { kontekst ->
            val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, 1.februar, Arbeidssituasjon.SelvstendigNæringsdrivende)
            kontekst.opptjeningsprøvinger.lagre(påbegynt.prøving)

            // Innenfor transaksjonen ser vi vår egen skriving ...
            assertNotNull(kontekst.opptjeningsprøvinger.finnSiste(FØDSELSNUMMER, 1.februar))
            // ... men en annen forbindelse gjør det ikke.
            assertEquals(0, Database.antallRader("kravproving"))
        }

        assertEquals(1, Database.antallRader("kravproving"))
    }
}
