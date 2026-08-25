package no.nav.helse.sykepenger.vilkarsproving.infra.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OpptjeningsgrunnlagDto.Arbeidstaker::class, name = "ARBEIDSTAKER"),
    JsonSubTypes.Type(value = OpptjeningsgrunnlagDto.SelvstendigNæringsdrivende::class, name = "SELVSTENDIG_NÆRINGSDRIVENDE"),
)
internal sealed interface VilkårsgrunnlagDto

internal sealed interface OpptjeningsgrunnlagDto : VilkårsgrunnlagDto {
    data class Arbeidstaker(
        val arbeidsforhold: List<ArbeidsforholdDto>,
    ) : OpptjeningsgrunnlagDto

    class SelvstendigNæringsdrivende : OpptjeningsgrunnlagDto
}

internal data class ArbeidsforholdDto(
    val orgnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val type: ArbeidsforholdtypeDto,
)

internal enum class ArbeidsforholdtypeDto {
    FORENKLET_OPPGJØRSORDNING,
    FRILANSER,
    MARITIMT,
    ORDINÆRT,
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = UtledetDto.IngenUtledning::class, name = "INGEN_UTLEDNING"),
    JsonSubTypes.Type(value = UtledetDto.Opptjeningstid::class, name = "OPPTJENINGSTID"),
)
internal sealed interface UtledetDto {
    class IngenUtledning : UtledetDto

    data class Opptjeningstid(
        val opptjeningsperiodeFom: LocalDate?,
        val opptjeningsperiodeTom: LocalDate?,
        val opptjeningsdager: Int,
    ) : UtledetDto
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = VurderingskildeDto.Automatisk::class, name = "AUTOMATISK"),
    JsonSubTypes.Type(value = VurderingskildeDto.Saksbehandler::class, name = "SAKSBEHANDLER"),
    JsonSubTypes.Type(value = VurderingskildeDto.OverførtFraSpleis::class, name = "OVERFOERT_FRA_SPLEIS"),
)
internal sealed interface VurderingskildeDto {
    data class Automatisk(
        val prøvingId: java.util.UUID,
        val grunnlag: VilkårsgrunnlagDto,
        val utledet: UtledetDto,
        val versjonAvKildekode: String,
    ) : VurderingskildeDto

    data class Saksbehandler(
        val ident: String,
        val fritekstbegrunnelse: String,
    ) : VurderingskildeDto

    data class OverførtFraSpleis(
        val grunnlag: VilkårsgrunnlagDto,
        val utledet: UtledetDto,
    ) : VurderingskildeDto
}
