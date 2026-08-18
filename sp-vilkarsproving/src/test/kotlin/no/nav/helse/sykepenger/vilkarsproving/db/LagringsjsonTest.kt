package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Kilde
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
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
            """{"type":"ARBEIDSTAKER","arbeidsforhold":[{"orgnummer":"987654321","fom":"2018-01-01","tom":"2018-01-31","type":"ORDINÆRT"}]}""",
            Grunnlagsjson.tilJson(grunnlag),
        )
        assertEquals(grunnlag, grunnlagFraJson(grunnlag))
    }

    @Test
    fun `selvstendig næringsdrivende lagres på avtalt format`() {
        val grunnlag = Opptjeningsgrunnlag.SelvstendigNæringsdrivende

        assertEquals("""{"type":"SELVSTENDIG_NÆRINGSDRIVENDE"}""", Grunnlagsjson.tilJson(grunnlag))
        assertEquals(grunnlag, grunnlagFraJson(grunnlag))
    }

    // Et løpende arbeidsforhold har ansettelseperiode til LocalDate.MAX, som må overleve lagringen
    @Test
    fun `løpende arbeidsforhold beholder åpen sluttdato`() {
        val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = null))

        assertEquals(
            """{"type":"ARBEIDSTAKER","arbeidsforhold":[{"orgnummer":"987654321","fom":"2018-01-01","tom":"+999999999-12-31","type":"ORDINÆRT"}]}""",
            Grunnlagsjson.tilJson(grunnlag),
        )
        assertEquals(grunnlag, grunnlagFraJson(grunnlag))
    }

    // Mappingen mellom domeneenum og dto-enum er skrevet ut for hånd; her sjekkes at ingen verdi er glemt
    @Test
    fun `alle arbeidsforholdtyper tåler en rundtur`() {
        Arbeidsforhold.Arbeidsforholdtype.entries.forEach { type ->
            val grunnlag = arbeidstakergrunnlag(arbeidsforhold(ansattFom = 1.januar, ansattTom = 31.januar, type = type))
            assertEquals(grunnlag, grunnlagFraJson(grunnlag))
        }
    }

    @Test
    fun `flere arbeidsforhold beholder rekkefølgen`() {
        val grunnlag =
            arbeidstakergrunnlag(
                arbeidsforhold(orgnummer = "111111111", ansattFom = 1.januar, ansattTom = 15.januar),
                arbeidsforhold(orgnummer = "222222222", ansattFom = 16.januar, ansattTom = 31.januar),
            )

        assertEquals(grunnlag, grunnlagFraJson(grunnlag))
    }

    @Test
    fun `automatisk kilde lagres på avtalt format`() {
        val kilde = Kilde.Automatisk(regelversjon = "1")

        assertEquals("""{"type":"AUTOMATISK","regelversjon":"1"}""", Kildejson.tilJson(kilde))
        assertEquals(kilde, Kildejson.fraJson(Kildejson.tilJson(kilde)))
    }

    @Test
    fun `manuell kilde lagres på avtalt format`() {
        val kilde = Kilde.Manuell(saksbehandlerIdent = "A123456", fritekstbegrunnelse = "vurdert etter dialog med bruker")

        assertEquals(
            """{"type":"MANUELL","saksbehandlerIdent":"A123456","fritekstbegrunnelse":"vurdert etter dialog med bruker"}""",
            Kildejson.tilJson(kilde),
        )
        assertEquals(kilde, Kildejson.fraJson(Kildejson.tilJson(kilde)))
    }

    // Ukjent type betyr at raden er skrevet av en annen versjon enn den som leser: da skal vi feile, ikke gjette
    @Test
    fun `ukjent grunnlagstype gir feil`() {
        assertThrows<JacksonException> {
            Grunnlagsjson.fraJson(Vilkår.Opptjening, """{"type":"FISKER"}""")
        }
    }

    @Test
    fun `ukjent kildetype gir feil`() {
        assertThrows<JacksonException> {
            Kildejson.fraJson("""{"type":"MASKINELT"}""")
        }
    }

    // Under en rullering leser gamle podder rader skrevet av nye: et felt vi ikke kjenner skal ikke velte lesingen
    @Test
    fun `ukjent felt i lagret json ignoreres`() {
        val json = """{"type":"AUTOMATISK","regelversjon":"1","vurdertAv":"noe vi ikke kjenner"}"""

        assertEquals(Kilde.Automatisk(regelversjon = "1"), Kildejson.fraJson(json))
    }

    private fun grunnlagFraJson(grunnlag: Opptjeningsgrunnlag) = Grunnlagsjson.fraJson(grunnlag.vilkår, Grunnlagsjson.tilJson(grunnlag))
}
