package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.sykepenger.vilkarsproving.db.Database
import no.nav.helse.sykepenger.vilkarsproving.db.DatabaseTest
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Kilde
import no.nav.helse.sykepenger.vilkarsproving.domain.Kodeverkkode
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import no.nav.helse.sykepenger.vilkarsproving.infra.db.FØDSELSNUMMER
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidstakergrunnlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class PostgresVilkårsvurderingRepositoryTest : DatabaseTest() {
    // Grunnlaget følger vurderingen, slik at vi i ettertid kan svare på hva vurderingen gjelder.
    // Da må også et løpende arbeidsforhold (uten sluttdato) komme uendret tilbake.
    @Test
    fun `arbeidstakervurdering lagres og hentes tilbake med grunnlaget sitt`() {
        val grunnlag =
            arbeidstakergrunnlag(
                arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar),
                arbeidsforhold(orgnummer = "123456789", ansattFom = 1.februar, ansattTom = null),
            )
        val vurdering = lagreVurdering(grunnlag)

        val lagret = transaksjon { it.vilkårsvurderinger.finn(Vilkår.Opptjening, vurdering.id) }!!

        assertEquals(vurdering.id, lagret.id)
        assertEquals(vurdering.prøvingId, lagret.prøvingId)
        assertEquals(Vilkår.Opptjening, lagret.vilkår)
        assertEquals(FØDSELSNUMMER, lagret.fødselsnummer)
        assertEquals(1.februar, lagret.skjæringstidspunkt)
        assertEquals(grunnlag, lagret.grunnlag)
        assertEquals(vurdering.kodeverkkode, lagret.kodeverkkode)
        assertEquals(vurdering.utfall, lagret.utfall)
        assertEquals(Kilde.Automatisk("1"), lagret.kilde)
        assertEquals(vurdering.vurdertTidspunkt.truncatedTo(ChronoUnit.MILLIS), lagret.vurdertTidspunkt.truncatedTo(ChronoUnit.MILLIS))
    }

    @Test
    fun `vurdering av selvstendig næringsdrivende lagres og hentes tilbake`() {
        val vurdering = lagreVurdering(Opptjeningsgrunnlag.SelvstendigNæringsdrivende)

        val lagret = transaksjon { it.vilkårsvurderinger.finn(Vilkår.Opptjening, vurdering.id) }!!

        assertEquals(Opptjeningsgrunnlag.SelvstendigNæringsdrivende, lagret.grunnlag)
        assertEquals(Kodeverkkode.OPPTJENING_MINST_4_UKER, lagret.kodeverkkode)
    }

    // Manuell vurdering er en kilde, ikke en egen resultattype — også den må overleve lagringen
    @Test
    fun `manuell kilde lagres og hentes tilbake`() {
        val automatisk = lagreVurdering(arbeidstakergrunnlag())
        val vurdering =
            Vilkårsvurdering.manuell(
                prøvingId = automatisk.prøvingId,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                grunnlag = arbeidstakergrunnlag(),
                kodeverkkode = Kodeverkkode.IKKE_OPPTJENING_ARBEID_ELLER_YTELSE,
                saksbehandlerIdent = "A123456",
                fritekstbegrunnelse = "Ikke nok opptjening",
                vurdertTidspunkt = Instant.now(),
            )
        transaksjon { it.vilkårsvurderinger.lagre(vurdering) }

        val lagret = transaksjon { it.vilkårsvurderinger.finn(Vilkår.Opptjening, vurdering.id) }!!

        assertEquals(Kilde.Manuell("A123456", "Ikke nok opptjening"), lagret.kilde)
        assertEquals(Kodeverkkode.IKKE_OPPTJENING_ARBEID_ELLER_YTELSE, lagret.kodeverkkode)
    }

    // Vurderinger oppdateres aldri: en ny prøving gir en ny rad, og den nyeste er den gjeldende
    @Test
    fun `gjeldende er den sist lagrede vurderingen`() {
        val første = lagreVurdering(arbeidstakergrunnlag())
        val andre = lagreVurdering(Opptjeningsgrunnlag.SelvstendigNæringsdrivende)

        val gjeldende = transaksjon { it.vilkårsvurderinger.gjeldende(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) }!!

        assertEquals(andre.id, gjeldende.id)
        assertEquals(2, Database.antallRader("vilkarsvurdering"))
        assertEquals(første.id, transaksjon { it.vilkårsvurderinger.finn(Vilkår.Opptjening, første.id) }!!.id)
    }

    @Test
    fun `gjeldende skiller på skjæringstidspunkt og fødselsnummer`() {
        lagreVurdering(arbeidstakergrunnlag(), skjæringstidspunkt = 1.mars)

        assertNull(transaksjon { it.vilkårsvurderinger.gjeldende(Vilkår.Opptjening, FØDSELSNUMMER, 1.februar) })
        assertNull(transaksjon { it.vilkårsvurderinger.gjeldende(Vilkår.Opptjening, "12029240046", 1.mars) })
        assertEquals(1.mars, transaksjon { it.vilkårsvurderinger.gjeldende(Vilkår.Opptjening, FØDSELSNUMMER, 1.mars) }!!.skjæringstidspunkt)
    }

    @Test
    fun `samme vurdering kan ikke lagres to ganger`() {
        val vurdering = lagreVurdering(arbeidstakergrunnlag())

        assertThrows<IllegalStateException> { transaksjon { it.vilkårsvurderinger.lagre(vurdering) } }

        assertEquals(1, Database.antallRader("vilkarsvurdering"))
    }

    @Test
    fun `finn gir null for en ukjent vurdering`() {
        assertNull(transaksjon { it.vilkårsvurderinger.finn(Vilkår.Opptjening, VurderingId.ny()) })
    }

    // "Hent alt for person" (GET-endepunktet) må se alle vurderinger uavhengig av skjæringstidspunkt,
    // eldste først.
    @Test
    fun `finnAlle henter alle vurderinger for personen, eldste foerst`() {
        val første = lagreVurdering(arbeidstakergrunnlag(), skjæringstidspunkt = 1.januar)
        val andre = lagreVurdering(Opptjeningsgrunnlag.SelvstendigNæringsdrivende, skjæringstidspunkt = 1.februar)

        val alle = transaksjon { it.vilkårsvurderinger.finnAlle(FØDSELSNUMMER) }

        assertEquals(listOf(første.id, andre.id), alle.map { it.id })
    }

    @Test
    fun `finnAlle skiller paa fødselsnummer`() {
        lagreVurdering(arbeidstakergrunnlag())

        assertEquals(emptyList<Any>(), transaksjon { it.vilkårsvurderinger.finnAlle("12029240046") })
    }

    /**
     * Lagrer en vurdering gjennom den vanlige flyten: en prøving startes, fullføres og lagres
     * sammen med vurderingen. Da blir prøvingen avsluttet, og neste vurdering på samme nøkkel
     * kolliderer ikke med det unike indekset for aktive prøvinger.
     */
    private fun lagreVurdering(
        grunnlag: Vilkårsgrunnlag,
        skjæringstidspunkt: LocalDate = 1.februar,
    ): Vilkårsvurdering =
        transaksjon { kontekst ->
            when (grunnlag) {
                is Opptjeningsgrunnlag.Arbeidstaker -> {
                    val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, skjæringstidspunkt, Arbeidssituasjon.Arbeidstaker)
                    kontekst.vilkårsprøvinger.lagre(påbegynt.prøving)
                    val vurdering = påbegynt.prøving.motta(grunnlag)
                    kontekst.vilkårsvurderinger.lagre(vurdering)
                    kontekst.vilkårsprøvinger.lagre(påbegynt.prøving)
                    vurdering
                }

                else -> {
                    val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, skjæringstidspunkt, Arbeidssituasjon.SelvstendigNæringsdrivende)
                    kontekst.vilkårsprøvinger.lagre(påbegynt.prøving)
                    checkNotNull(påbegynt.vurdering).also { kontekst.vilkårsvurderinger.lagre(it) }
                }
            }
        }
}
