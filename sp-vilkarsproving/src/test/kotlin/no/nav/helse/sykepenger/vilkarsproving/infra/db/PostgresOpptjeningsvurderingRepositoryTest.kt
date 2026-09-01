package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.februar
import no.nav.helse.januar
import no.nav.helse.mars
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidssituasjon
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsprøving
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PostgresOpptjeningsvurderingRepositoryTest : DatabaseTest() {
    @Test
    fun `arbeidstakervurdering lagres og hentes tilbake med grunnlaget sitt`() {
        val grunnlag =
            arbeidstakergrunnlag(
                arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar),
                arbeidsforhold(orgnummer = "123456789", ansattFom = 1.februar, ansattTom = null),
            )
        val vurdering = lagreVurdering(grunnlag)

        val lagret = transaksjon { it.opptjeningsvurderinger.finn(vurdering.id) } as Opptjeningsvurdering.VurdertISpeil

        assertEquals(vurdering.id, lagret.id)
        assertEquals(FØDSELSNUMMER, lagret.fødselsnummer)
        assertEquals(1.februar, lagret.skjæringstidspunkt)
        assertEquals(vurdering.girRettTilSykepenger, lagret.girRettTilSykepenger)
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

        val lagret = transaksjon { it.opptjeningsvurderinger.finn(vurdering.id) } as Opptjeningsvurdering.VurdertISpeil
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
            Opptjeningsvurdering.avSaksbehandler(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                sti = listOf(ledd),
            )
        transaksjon { it.opptjeningsvurderinger.lagre(vurdering) }

        val lagret = transaksjon { it.opptjeningsvurderinger.finn(vurdering.id) } as Opptjeningsvurdering.VurdertISpeil
        val kilde = lagret.vilkårsvurderinger.single().kilde

        assertInstanceOf(Vurderingskilde.Saksbehandler::class.java, kilde)
        assertEquals("A123456", (kilde as Vurderingskilde.Saksbehandler).ident)
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, lagret.avgjørendeVilkårskode)
        assertFalse(lagret.girRettTilSykepenger)
    }

    @Test
    fun `infotrygdvurdering lagres uten sti`() {
        val vurdering =
            Opptjeningsvurdering.fraInfotrygd(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                girRettTilSykepenger = true,
            )
        transaksjon { it.opptjeningsvurderinger.lagre(vurdering) }

        val lagret = transaksjon { it.opptjeningsvurderinger.finn(vurdering.id) }

        assertInstanceOf(Opptjeningsvurdering.OverførtFraInfotrygd::class.java, lagret)
        assertTrue(lagret!!.girRettTilSykepenger)
    }

    @Test
    fun `gjeldende er den sist lagrede vurderingen`() {
        val første = lagreVurdering(arbeidstakergrunnlag())
        val andre = lagreVurdering(Opptjeningsgrunnlag.SelvstendigNæringsdrivende)

        val gjeldende = transaksjon { it.opptjeningsvurderinger.gjeldende(FØDSELSNUMMER, 1.februar) }!!

        assertEquals(andre.id, gjeldende.id)
        assertEquals(2, Database.antallRader("kravvurdering"))
        assertEquals(første.id, transaksjon { it.opptjeningsvurderinger.finn(første.id) }!!.id)
    }

    @Test
    fun `gjeldende skiller på skjæringstidspunkt og fødselsnummer`() {
        lagreVurdering(arbeidstakergrunnlag(), skjæringstidspunkt = 1.mars)

        assertNull(transaksjon { it.opptjeningsvurderinger.gjeldende(FØDSELSNUMMER, 1.februar) })
        assertNull(transaksjon { it.opptjeningsvurderinger.gjeldende("12029240046", 1.mars) })
        assertEquals(
            1.mars,
            transaksjon { it.opptjeningsvurderinger.gjeldende(FØDSELSNUMMER, 1.mars) }!!.skjæringstidspunkt,
        )
    }

    @Test
    fun `samme vurdering kan ikke lagres to ganger`() {
        val vurdering = lagreVurdering(arbeidstakergrunnlag())

        assertThrows<IllegalStateException> { transaksjon { it.opptjeningsvurderinger.lagre(vurdering) } }

        assertEquals(1, Database.antallRader("kravvurdering"))
    }

    @Test
    fun `finn gir null for en ukjent vurdering`() {
        assertNull(transaksjon { it.opptjeningsvurderinger.finn(OpptjeningsvurderingId.ny()) })
    }

    private fun lagreVurdering(
        grunnlag: Opptjeningsgrunnlag,
        skjæringstidspunkt: LocalDate = 1.februar,
    ): Opptjeningsvurdering.VurdertISpeil =
        transaksjon { kontekst ->
            when (grunnlag) {
                is Opptjeningsgrunnlag.Arbeidstaker -> {
                    val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, skjæringstidspunkt, Arbeidssituasjon.Arbeidstaker)
                    kontekst.opptjeningsprøvinger.lagre(påbegynt.prøving)
                    val vurdering = påbegynt.prøving.motta(grunnlag)
                    kontekst.opptjeningsvurderinger.lagre(vurdering)
                    kontekst.opptjeningsprøvinger.lagre(påbegynt.prøving)
                    vurdering
                }

                else -> {
                    val påbegynt = Opptjeningsprøving.start(FØDSELSNUMMER, skjæringstidspunkt, Arbeidssituasjon.SelvstendigNæringsdrivende)
                    kontekst.opptjeningsprøvinger.lagre(påbegynt.prøving)
                    checkNotNull(påbegynt.vurdering).also { kontekst.opptjeningsvurderinger.lagre(it) }
                }
            }
        }
}
