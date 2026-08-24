package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.hendelser.Periode
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opphav
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val objectMapper = jacksonObjectMapper()

/**
 * Opphavet — inkludert grunnlaget, for vurderinger som har et — lagres som json i én kolonne.
 *
 * Vilkåret raden gjelder står i egen kolonne og sendes inn ved lesing, slik at vi ikke lagrer det to
 * ganger. Selve json-en er selvbeskrivende for øvrig: hvilken dto den skal leses som følger av
 * `type`-diskriminatorene.
 */
internal object Opphavsjson {
    fun tilJson(opphav: Opphav): String = objectMapper.writeValueAsString(opphav.tilDto())

    fun fraJson(
        vilkår: Vilkår,
        json: String,
    ): Opphav = objectMapper.readValue<OpphavDto>(json).tilOpphav(vilkår)
}

private fun Opphav.tilDto(): OpphavDto =
    when (this) {
        is Opphav.Automatisk -> OpphavDto.Automatisk(grunnlag = grunnlag.tilDto(), versjonAvKildekode = versjonAvKildekode)
        is Opphav.Saksbehandler -> OpphavDto.Saksbehandler(ident = ident, fritekstbegrunnelse = fritekstbegrunnelse)
        is Opphav.Infotrygd -> OpphavDto.Infotrygd()
    }

private fun OpphavDto.tilOpphav(vilkår: Vilkår): Opphav =
    when (this) {
        is OpphavDto.Automatisk -> Opphav.Automatisk(grunnlag = grunnlag.tilVilkårsgrunnlag(), versjonAvKildekode = versjonAvKildekode)
        is OpphavDto.Saksbehandler -> Opphav.Saksbehandler(vilkår = vilkår, ident = ident, fritekstbegrunnelse = fritekstbegrunnelse)
        is OpphavDto.Infotrygd -> Opphav.Infotrygd(vilkår)
    }

private fun Vilkårsgrunnlag.tilDto(): VilkårsgrunnlagDto =
    when (this) {
        is Opptjeningsgrunnlag -> tilDto()
    }

private fun VilkårsgrunnlagDto.tilVilkårsgrunnlag(): Vilkårsgrunnlag =
    when (this) {
        is OpptjeningsgrunnlagDto -> tilOpptjeningsgrunnlag()
    }

private fun Opptjeningsgrunnlag.tilDto(): OpptjeningsgrunnlagDto =
    when (this) {
        is Opptjeningsgrunnlag.Arbeidstaker -> OpptjeningsgrunnlagDto.Arbeidstaker(arbeidsforhold.map { it.tilDto() })
        Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> OpptjeningsgrunnlagDto.SelvstendigNæringsdrivende()
    }

private fun OpptjeningsgrunnlagDto.tilOpptjeningsgrunnlag(): Opptjeningsgrunnlag =
    when (this) {
        is OpptjeningsgrunnlagDto.Arbeidstaker -> Opptjeningsgrunnlag.Arbeidstaker(arbeidsforhold.map { it.tilArbeidsforhold() })
        is OpptjeningsgrunnlagDto.SelvstendigNæringsdrivende -> Opptjeningsgrunnlag.SelvstendigNæringsdrivende
    }

private fun Arbeidsforhold.tilDto() =
    ArbeidsforholdDto(
        orgnummer = orgnummer,
        fom = ansettelseperiode.start,
        tom = ansettelseperiode.endInclusive,
        type =
            when (type) {
                Arbeidsforhold.Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING -> ArbeidsforholdtypeDto.FORENKLET_OPPGJØRSORDNING
                Arbeidsforhold.Arbeidsforholdtype.FRILANSER -> ArbeidsforholdtypeDto.FRILANSER
                Arbeidsforhold.Arbeidsforholdtype.MARITIMT -> ArbeidsforholdtypeDto.MARITIMT
                Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT -> ArbeidsforholdtypeDto.ORDINÆRT
            },
    )

private fun ArbeidsforholdDto.tilArbeidsforhold() =
    Arbeidsforhold(
        orgnummer = orgnummer,
        ansettelseperiode = Periode(fom, tom),
        type =
            when (type) {
                ArbeidsforholdtypeDto.FORENKLET_OPPGJØRSORDNING -> Arbeidsforhold.Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING
                ArbeidsforholdtypeDto.FRILANSER -> Arbeidsforhold.Arbeidsforholdtype.FRILANSER
                ArbeidsforholdtypeDto.MARITIMT -> Arbeidsforhold.Arbeidsforholdtype.MARITIMT
                ArbeidsforholdtypeDto.ORDINÆRT -> Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
            },
    )
