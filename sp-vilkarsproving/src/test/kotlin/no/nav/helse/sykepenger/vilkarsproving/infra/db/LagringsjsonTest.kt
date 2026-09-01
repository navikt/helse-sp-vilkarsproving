package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.UtledetFakta
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.core.JacksonException
import java.util.UUID

internal class LagringsjsonTest {
    @Test
    fun `automatisk arbeidstakervurdering lagres på avtalt format`() {
        val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar))
        val kilde = automatisk(grunnlag, UtledetFakta.Opptjeningstid(null, 0))

        assertEquals(
            """{"type":"AUTOMATISK","prøvingId":"$PRØVING_ID",""" +
                """"grunnlag":{"type":"ARBEIDSTAKER","arbeidsforhold":[{"orgnummer":"987654321","fom":"2018-01-01","tom":"2018-01-31","type":"ORDINÆRT"}]},""" +
                """"utledet":{"type":"OPPTJENINGSTID","opptjeningsperiodeFom":null,"opptjeningsperiodeTom":null,"opptjeningsdager":0},""" +
                """"versjonAvKildekode":"1"}""",
            Vurderingskildejson.tilJson(kilde),
        )
        assertEquals(kilde, rundtur(kilde))
    }

    @Test
    fun `selvstendig næringsdrivende lagres på avtalt format`() {
        val kilde = automatisk(Opptjeningsgrunnlag.SelvstendigNæringsdrivende, UtledetFakta.Ingen)

        assertEquals(
            """{"type":"AUTOMATISK","prøvingId":"$PRØVING_ID",""" +
                """"grunnlag":{"type":"SELVSTENDIG_NÆRINGSDRIVENDE"},""" +
                """"utledet":{"type":"INGEN_UTLEDNING"},""" +
                """"versjonAvKildekode":"1"}""",
            Vurderingskildejson.tilJson(kilde),
        )
        assertEquals(kilde, rundtur(kilde))
    }

    @Test
    fun `løpende arbeidsforhold beholder åpen sluttdato`() {
        val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = null))
        val kilde = automatisk(grunnlag, UtledetFakta.Opptjeningstid(null, 0))

        assertEquals(
            """{"type":"AUTOMATISK","prøvingId":"$PRØVING_ID",""" +
                """"grunnlag":{"type":"ARBEIDSTAKER","arbeidsforhold":[{"orgnummer":"987654321","fom":"2018-01-01","tom":"+999999999-12-31","type":"ORDINÆRT"}]},""" +
                """"utledet":{"type":"OPPTJENINGSTID","opptjeningsperiodeFom":null,"opptjeningsperiodeTom":null,"opptjeningsdager":0},""" +
                """"versjonAvKildekode":"1"}""",
            Vurderingskildejson.tilJson(kilde),
        )
        assertEquals(kilde, rundtur(kilde))
    }

    @Test
    fun `alle arbeidsforholdtyper tåler en rundtur`() {
        Arbeidsforhold.Arbeidsforholdtype.entries.forEach { type ->
            val grunnlag =
                arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar, type = type))
            val kilde = automatisk(grunnlag, UtledetFakta.Ingen)
            assertEquals(kilde, rundtur(kilde))
        }
    }

    @Test
    fun `flere arbeidsforhold beholder rekkefølgen`() {
        val grunnlag =
            arbeidstakergrunnlag(
                arbeidsforhold(orgnummer = "111111111", ansattFom = 1.januar, ansattTom = 15.januar),
                arbeidsforhold(orgnummer = "222222222", ansattFom = 16.januar, ansattTom = 31.januar),
            )
        val kilde = automatisk(grunnlag, UtledetFakta.Ingen)

        assertEquals(kilde, rundtur(kilde))
    }

    @Test
    fun `opptjeningsperiode og opptjeningsdager lagres sammen med grunnlaget`() {
        val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 4.januar, ansattTom = 31.januar))
        val kilde = automatisk(grunnlag, UtledetFakta.Opptjeningstid(4.januar til 31.januar, 28))

        val etterRundtur = rundtur(kilde) as Vurderingskilde.Automatisk
        val utledetFakta = etterRundtur.utledetFakta as UtledetFakta.Opptjeningstid
        assertEquals(4.januar, utledetFakta.opptjeningsperiode?.start)
        assertEquals(31.januar, utledetFakta.opptjeningsperiode?.endInclusive)
        assertEquals(28, utledetFakta.opptjeningsdager)
    }

    @Test
    fun `saksbehandlerkilde lagres på avtalt format`() {
        val kilde = Vurderingskilde.Saksbehandler(ident = "A123456", fritekstbegrunnelse = "vurdert etter dialog med bruker")

        assertEquals(
            """{"type":"SAKSBEHANDLER","ident":"A123456","fritekstbegrunnelse":"vurdert etter dialog med bruker"}""",
            Vurderingskildejson.tilJson(kilde),
        )
        assertEquals(kilde, rundtur(kilde))
    }

    @Test
    fun `overført fra spleis lagres på avtalt format`() {
        val grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende
        val kilde = Vurderingskilde.OverførtFraSpleis(grunnlag = grunnlag, utledetFakta = UtledetFakta.Ingen)

        assertEquals(
            """{"type":"OVERFOERT_FRA_SPLEIS",""" +
                """"grunnlag":{"type":"SELVSTENDIG_NÆRINGSDRIVENDE"},""" +
                """"utledet":{"type":"INGEN_UTLEDNING"}}""",
            Vurderingskildejson.tilJson(kilde),
        )
        assertEquals(kilde, rundtur(kilde))
    }

    @Test
    fun `ukjent grunnlagstype gir feil`() {
        assertThrows<JacksonException> {
            Vurderingskildejson.fraJson(
                """{"type":"AUTOMATISK","prøvingId":"$PRØVING_ID","grunnlag":{"type":"FISKER"},""" +
                    """"utledet":{"type":"INGEN_UTLEDNING"},"versjonAvKildekode":"1"}""",
            )
        }
    }

    @Test
    fun `ukjent kildetype gir feil`() {
        assertThrows<JacksonException> {
            Vurderingskildejson.fraJson("""{"type":"MASKINELT"}""")
        }
    }

    @Test
    fun `ukjent felt i lagret json ignoreres`() {
        val json = """{"type":"SAKSBEHANDLER","ident":"A123456","fritekstbegrunnelse":"","vurdertAv":"noe vi ikke kjenner"}"""

        assertEquals(
            Vurderingskilde.Saksbehandler(ident = "A123456", fritekstbegrunnelse = ""),
            Vurderingskildejson.fraJson(json),
        )
    }

    private fun automatisk(
        grunnlag: Opptjeningsgrunnlag,
        utledetFakta: UtledetFakta,
    ) = Vurderingskilde.Automatisk(PRØVING_ID, grunnlag, utledetFakta, versjonAvKildekode = "1")

    private fun rundtur(kilde: Vurderingskilde) = Vurderingskildejson.fraJson(Vurderingskildejson.tilJson(kilde))

    private companion object {
        val PRØVING_ID = OpptjeningsprøvingId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    }
}
