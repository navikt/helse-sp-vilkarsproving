package no.nav.helse.sykepenger.vilkarsproving.db

import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opphav
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.infra.db.Opphavsjson
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.infra.db.arbeidstakergrunnlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.core.JacksonException

/**
 * Lagringsformatet er en kontrakt: rader som allerede står i databasen må kunne leses av morgendagens
 * kode. Derfor sjekkes den nøyaktige json-en her, ikke bare at en rundtur gir samme objekt.
 */
internal class LagringsjsonTest {
    @Test
    fun `arbeidstakergrunnlag lagres på avtalt format`() {
        val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar))

        assertEquals(
            """{"type":"AUTOMATISK","grunnlag":{"type":"ARBEIDSTAKER","arbeidsforhold":[{"orgnummer":"987654321","fom":"2018-01-01","tom":"2018-01-31","type":"ORDINÆRT"}]},"versjonAvKildekode":"1"}""",
            Opphavsjson.tilJson(automatisk(grunnlag)),
        )
        assertEquals(grunnlag, grunnlagEtterRundtur(grunnlag))
    }

    @Test
    fun `selvstendig næringsdrivende lagres på avtalt format`() {
        val grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende

        assertEquals(
            """{"type":"AUTOMATISK","grunnlag":{"type":"SELVSTENDIG_NÆRINGSDRIVENDE"},"versjonAvKildekode":"1"}""",
            Opphavsjson.tilJson(automatisk(grunnlag)),
        )
        assertEquals(grunnlag, grunnlagEtterRundtur(grunnlag))
    }

    // Et løpende arbeidsforhold har ansettelseperiode til LocalDate.MAX, som må overleve lagringen
    @Test
    fun `løpende arbeidsforhold beholder åpen sluttdato`() {
        val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = null))

        assertEquals(
            """{"type":"AUTOMATISK","grunnlag":{"type":"ARBEIDSTAKER","arbeidsforhold":[{"orgnummer":"987654321","fom":"2018-01-01","tom":"+999999999-12-31","type":"ORDINÆRT"}]},"versjonAvKildekode":"1"}""",
            Opphavsjson.tilJson(automatisk(grunnlag)),
        )
        assertEquals(grunnlag, grunnlagEtterRundtur(grunnlag))
    }

    // Mappingen mellom domeneenum og dto-enum er skrevet ut for hånd; her sjekkes at ingen verdi er glemt
    @Test
    fun `alle arbeidsforholdtyper tåler en rundtur`() {
        Arbeidsforhold.Arbeidsforholdtype.entries.forEach { type ->
            val grunnlag =
                arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar, type = type))
            assertEquals(grunnlag, grunnlagEtterRundtur(grunnlag))
        }
    }

    @Test
    fun `flere arbeidsforhold beholder rekkefølgen`() {
        val grunnlag =
            arbeidstakergrunnlag(
                arbeidsforhold(orgnummer = "111111111", ansattFom = 1.januar, ansattTom = 15.januar),
                arbeidsforhold(orgnummer = "222222222", ansattFom = 16.januar, ansattTom = 31.januar),
            )

        assertEquals(grunnlag, grunnlagEtterRundtur(grunnlag))
    }

    @Test
    fun `saksbehandleropphav lagres på avtalt format`() {
        val opphav = Opphav.Saksbehandler(Vilkår.Opptjening, ident = "A123456", fritekstbegrunnelse = "vurdert etter dialog med bruker")

        assertEquals(
            """{"type":"SAKSBEHANDLER","ident":"A123456","fritekstbegrunnelse":"vurdert etter dialog med bruker"}""",
            Opphavsjson.tilJson(opphav),
        )
        assertEquals(opphav, rundtur(opphav))
    }

    // Fra Infotrygd har vi verken grunnlag eller saksbehandler; opphavet er kun en markør
    @Test
    fun `infotrygdopphav lagres på avtalt format`() {
        val opphav = Opphav.Infotrygd(Vilkår.Opptjening)

        assertEquals("""{"type":"INFOTRYGD"}""", Opphavsjson.tilJson(opphav))
        assertEquals(opphav, rundtur(opphav))
    }

    // Ukjent type betyr at raden er skrevet av en annen versjon enn den som leser: da skal vi feile, ikke gjette
    @Test
    fun `ukjent grunnlagstype gir feil`() {
        assertThrows<JacksonException> {
            Opphavsjson.fraJson(Vilkår.Opptjening, """{"type":"AUTOMATISK","grunnlag":{"type":"FISKER"},"versjonAvKildekode":"1"}""")
        }
    }

    @Test
    fun `ukjent opphavstype gir feil`() {
        assertThrows<JacksonException> {
            Opphavsjson.fraJson(Vilkår.Opptjening, """{"type":"MASKINELT"}""")
        }
    }

    // Under en rullering leser gamle podder rader skrevet av nye: et felt vi ikke kjenner skal ikke velte lesingen
    @Test
    fun `ukjent felt i lagret json ignoreres`() {
        val json = """{"type":"SAKSBEHANDLER","ident":"A123456","fritekstbegrunnelse":"","vurdertAv":"noe vi ikke kjenner"}"""

        assertEquals(
            Opphav.Saksbehandler(Vilkår.Opptjening, ident = "A123456", fritekstbegrunnelse = ""),
            Opphavsjson.fraJson(Vilkår.Opptjening, json),
        )
    }

    private fun automatisk(grunnlag: Vilkårsgrunnlag) = Opphav.Automatisk(grunnlag, versjonAvKildekode = "1")

    private fun rundtur(opphav: Opphav) = Opphavsjson.fraJson(opphav.vilkår, Opphavsjson.tilJson(opphav))

    private fun grunnlagEtterRundtur(grunnlag: Vilkårsgrunnlag) = (rundtur(automatisk(grunnlag)) as Opphav.Automatisk).grunnlag
}
