package no.nav.helse.sykepenger.vilkarsproving.infra.db

import no.nav.helse.hendelser.Periode
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utledet
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

private val objectMapper = jacksonObjectMapper()

/**
 * Kilden — inkludert grunnlaget og det som ble utledet, for vurderinger som har det — lagres som json i
 * én kolonne. Selve json-en er selvbeskrivende: hvilken dto den skal leses som følger av
 * `type`-diskriminatorene.
 */
internal object Vurderingskildejson {
    fun tilJson(kilde: Vurderingskilde): String = objectMapper.writeValueAsString(kilde.tilDto())

    fun fraJson(json: String): Vurderingskilde = objectMapper.readValue<VurderingskildeDto>(json).tilVurderingskilde()
}

private fun Vurderingskilde.tilDto(): VurderingskildeDto =
    when (this) {
        is Vurderingskilde.Automatisk ->
            VurderingskildeDto.Automatisk(
                prøvingId = prøvingId.value,
                grunnlag = grunnlag.tilDto(),
                utledet = utledet.tilDto(),
                versjonAvKildekode = versjonAvKildekode,
            )

        is Vurderingskilde.Saksbehandler -> VurderingskildeDto.Saksbehandler(ident = ident, fritekstbegrunnelse = fritekstbegrunnelse)

        is Vurderingskilde.OverførtFraSpleis ->
            VurderingskildeDto.OverførtFraSpleis(grunnlag = grunnlag.tilDto(), utledet = utledet.tilDto())
    }

private fun VurderingskildeDto.tilVurderingskilde(): Vurderingskilde =
    when (this) {
        is VurderingskildeDto.Automatisk ->
            Vurderingskilde.Automatisk(
                prøvingId = PrøvingId(prøvingId),
                grunnlag = grunnlag.tilVilkårsgrunnlag(),
                utledet = utledet.tilUtledet(),
                versjonAvKildekode = versjonAvKildekode,
            )

        is VurderingskildeDto.Saksbehandler -> Vurderingskilde.Saksbehandler(ident = ident, fritekstbegrunnelse = fritekstbegrunnelse)

        is VurderingskildeDto.OverførtFraSpleis ->
            Vurderingskilde.OverførtFraSpleis(grunnlag = grunnlag.tilVilkårsgrunnlag(), utledet = utledet.tilUtledet())
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

private fun Utledet.tilDto(): UtledetDto =
    when (this) {
        Utledet.IngenUtledning -> UtledetDto.IngenUtledning()
        is Utledet.Opptjeningstid ->
            UtledetDto.Opptjeningstid(
                opptjeningsperiodeFom = opptjeningsperiode?.start,
                opptjeningsperiodeTom = opptjeningsperiode?.endInclusive,
                opptjeningsdager = opptjeningsdager,
            )
    }

private fun UtledetDto.tilUtledet(): Utledet =
    when (this) {
        is UtledetDto.IngenUtledning -> Utledet.IngenUtledning
        is UtledetDto.Opptjeningstid ->
            Utledet.Opptjeningstid(
                opptjeningsperiode =
                    if (opptjeningsperiodeFom != null && opptjeningsperiodeTom != null) {
                        Periode(opptjeningsperiodeFom, opptjeningsperiodeTom)
                    } else {
                        null
                    },
                opptjeningsdager = opptjeningsdager,
            )
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
