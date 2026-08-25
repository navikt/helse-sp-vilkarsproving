package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import no.nav.helse.sykepenger.vilkarsproving.infra.db.FØDSELSNUMMER
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidstakergrunnlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal class PostgresKravvurderingRepositoryTest : DatabaseTest() {
    @Test
    fun `arbeidstakervurdering lagres og hentes tilbake med grunnlaget sitt`() {
        val grunnlag =
            arbeidstakergrunnlag(
                arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar),
                arbeidsforhold(orgnummer = "123456789", ansattFom = 1.februar, ansattTom = null),
            )
        val vurdering = lagreVurdering(grunnlag)

        val lagret = transaksjon { it.kravvurderinger.finn(Krav.Opptjening, vurdering.id) } as Kravvurdering.VurdertISpeil

        assertEquals(vurdering.id, lagret.id)
        assertEquals(Krav.Opptjening, lagret.krav)
        assertEquals(FØDSELSNUMMER, lagret.fødselsnummer)
        assertEquals(1.februar, lagret.skjæringstidspunkt)
        assertEquals(vurdering.utfall, lagret.utfall)
        assertEquals(vurdering.avgjørendeVilkårskode, lagret.avgjørendeVilkårskode)

        val ledd = lagret.vilkårsvurderinger.single()
        val kilde = ledd.kilde as Vurderingskilde.Automatisk
        assertEquals(grunnlag, kilde.grunnlag)
        assertEquals(
            vurdering.vilkårsvurderinger
                .single()
                .vurdertTidspunkt!!
                .truncatedTo(ChronoUnit.MILLIS),
            ledd.vurdertTidspunkt!!.truncatedTo(ChronoUnit.MILLIS),
        )
    }

    @Test
    fun `vurdering av selvstendig næringsdrivende lagres og hentes tilbake`() {
        val vurdering = lagreVurdering(Opptjeningsgrunnlag.SelvstendigNæringsdrivende)

        val lagret = transaksjon { it.kravvurderinger.finn(Krav.Opptjening, vurdering.id) } as Kravvurdering.VurdertISpeil
        val kilde = lagret.vilkårsvurderinger.single().kilde as Vurderingskilde.Automatisk

        assertEquals(Opptjeningsgrunnlag.SelvstendigNæringsdrivende, kilde.grunnlag)
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, lagret.avgjørendeVilkårskode)
    }

    @Test
    fun `saksbehandlervurdering lagres og hentes tilbake`() {
        val ledd =
            Vilkårsvurdering.avSaksbehandler(
                vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                utfall = Utfall.IkkeOppfylt,
                saksbehandlerIdent = "A123456",
                fritekstbegrunnelse = "Ikke nok opptjening",
                vurdertTidspunkt = Instant.now(),
            )
        val vurdering =
            Kravvurdering.avSaksbehandler(
                krav = Krav.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            )
        transaksjon { it.kravvurderinger.lagre(vurdering) }

        val lagret = transaksjon { it.kravvurderinger.finn(Krav.Opptjening, vurdering.id) } as Kravvurdering.VurdertISpeil
        val kilde = lagret.vilkårsvurderinger.single().kilde

        assertInstanceOf(Vurderingskilde.Saksbehandler::class.java, kilde)
        assertEquals("A123456", (kilde as Vurderingskilde.Saksbehandler).ident)
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, lagret.avgjørendeVilkårskode)
        assertEquals(Utfall.IkkeOppfylt, lagret.utfall)
    }

    @Test
    fun `infotrygdvurdering lagres uten sti`() {
        val vurdering =
            Kravvurdering.fraInfotrygd(
                krav = Krav.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                utfall = Utfall.Oppfylt,
            )
        transaksjon { it.kravvurderinger.lagre(vurdering) }

        val lagret = transaksjon { it.kravvurderinger.finn(Krav.Opptjening, vurdering.id) }

        assertInstanceOf(Kravvurdering.OverførtFraInfotrygd::class.java, lagret)
        assertEquals(Utfall.Oppfylt, lagret!!.utfall)
    }

    @Test
    fun `gjeldende er den sist lagrede vurderingen`() {
        val første = lagreVurdering(arbeidstakergrunnlag())
        val andre = lagreVurdering(Opptjeningsgrunnlag.SelvstendigNæringsdrivende)

        val gjeldende = transaksjon { it.kravvurderinger.gjeldende(Krav.Opptjening, FØDSELSNUMMER, 1.februar) }!!

        assertEquals(andre.id, gjeldende.id)
        assertEquals(2, Database.antallRader("kravvurdering"))
        assertEquals(første.id, transaksjon { it.kravvurderinger.finn(Krav.Opptjening, første.id) }!!.id)
    }

    @Test
    fun `gjeldende skiller på skjæringstidspunkt og fødselsnummer`() {
        lagreVurdering(arbeidstakergrunnlag(), skjæringstidspunkt = 1.mars)

        assertNull(transaksjon { it.kravvurderinger.gjeldende(Krav.Opptjening, FØDSELSNUMMER, 1.februar) })
        assertNull(transaksjon { it.kravvurderinger.gjeldende(Krav.Opptjening, "12029240046", 1.mars) })
        assertEquals(
            1.mars,
            transaksjon { it.kravvurderinger.gjeldende(Krav.Opptjening, FØDSELSNUMMER, 1.mars) }!!.skjæringstidspunkt,
        )
    }

    @Test
    fun `samme vurdering kan ikke lagres to ganger`() {
        val vurdering = lagreVurdering(arbeidstakergrunnlag())

        assertThrows<IllegalStateException> { transaksjon { it.kravvurderinger.lagre(vurdering) } }

        assertEquals(1, Database.antallRader("kravvurdering"))
    }

    @Test
    fun `finn gir null for en ukjent vurdering`() {
        assertNull(transaksjon { it.kravvurderinger.finn(Krav.Opptjening, KravvurderingId.ny()) })
    }

    private fun lagreVurdering(
        grunnlag: Vilkårsgrunnlag,
        skjæringstidspunkt: LocalDate = 1.februar,
    ): Kravvurdering.VurdertISpeil =
        transaksjon { kontekst ->
            when (grunnlag) {
                is Opptjeningsgrunnlag.Arbeidstaker -> {
                    val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, skjæringstidspunkt, Arbeidssituasjon.Arbeidstaker)
                    kontekst.kravprøvinger.lagre(påbegynt.prøving)
                    val vurdering = påbegynt.prøving.motta(grunnlag)
                    kontekst.kravvurderinger.lagre(vurdering)
                    kontekst.kravprøvinger.lagre(påbegynt.prøving)
                    vurdering
                }

                else -> {
                    val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, skjæringstidspunkt, Arbeidssituasjon.SelvstendigNæringsdrivende)
                    kontekst.kravprøvinger.lagre(påbegynt.prøving)
                    checkNotNull(påbegynt.vurdering).also { kontekst.kravvurderinger.lagre(it) }
                }
            }
        }
}
