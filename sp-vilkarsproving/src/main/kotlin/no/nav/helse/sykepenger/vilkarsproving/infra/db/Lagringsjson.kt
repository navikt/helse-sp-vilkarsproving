package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.hendelser.Periode
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Kilde
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val objectMapper = jacksonObjectMapper()

/**
 * Grunnlaget lagres som json. Hvilken dto json-en skal leses som følger av vilkåret raden gjelder,
 * så vi trenger ikke en felles typediskriminator på tvers av vilkår.
 */
internal object Grunnlagsjson {
    fun tilJson(grunnlag: Vilkårsgrunnlag): String =
        objectMapper.writeValueAsString(
            when (grunnlag) {
                is Opptjeningsgrunnlag -> grunnlag.tilDto()
            },
        )

    fun fraJson(
        vilkår: Vilkår,
        json: String,
    ): Vilkårsgrunnlag =
        when (vilkår) {
            Vilkår.Opptjening -> objectMapper.readValue<OpptjeningsgrunnlagDto>(json).tilOpptjeningsgrunnlag()
        }
}

internal object Kildejson {
    fun tilJson(kilde: Kilde): String = objectMapper.writeValueAsString(kilde.tilDto())

    fun fraJson(json: String): Kilde = objectMapper.readValue<KildeDto>(json).tilKilde()
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

private fun Kilde.tilDto(): KildeDto =
    when (this) {
        is Kilde.Automatisk -> KildeDto.Automatisk(regelversjon)
        is Kilde.Manuell -> KildeDto.Manuell(saksbehandlerIdent = saksbehandlerIdent, fritekstbegrunnelse = fritekstbegrunnelse)
    }

private fun KildeDto.tilKilde(): Kilde =
    when (this) {
        is KildeDto.Automatisk -> Kilde.Automatisk(regelversjon)
        is KildeDto.Manuell -> Kilde.Manuell(saksbehandlerIdent = saksbehandlerIdent, fritekstbegrunnelse = fritekstbegrunnelse)
    }
