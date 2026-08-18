package no.nav.helse.sykepenger.vilkarsproving.infra.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

/**
 * Lagringsformatet for grunnlag og kilde, uttrykt som dataklasser.
 *
 * Dtoene er kontrakten mot databasen, og er bevisst skilt fra domenetypene: domenet kan
 * refaktoreres — nye felter, andre navn, delt opp i flere typer — uten at lagrede vurderinger blir
 * uleselige. Prisen er en eksplisitt mapping (se `Lagringsjson.kt`), der `when`-uttrykkene er
 * uttømmende slik at kompilatoren krever et valg når domenet utvides.
 *
 * Typenavnene i `@JsonSubTypes` er skrevet ut for hånd av samme grunn: de skal ikke følge
 * klassenavnene.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OpptjeningsgrunnlagDto.Arbeidstaker::class, name = "ARBEIDSTAKER"),
    JsonSubTypes.Type(value = OpptjeningsgrunnlagDto.SelvstendigNæringsdrivende::class, name = "SELVSTENDIG_NÆRINGSDRIVENDE"),
)
internal sealed interface OpptjeningsgrunnlagDto {
    data class Arbeidstaker(
        val arbeidsforhold: List<ArbeidsforholdDto>,
    ) : OpptjeningsgrunnlagDto

    /**
     * Klasse og ikke `data object`: Jackson lager en ny instans ved lesing, og et objekt ville da
     * vært avhengig av singleton-støtte i Kotlin-modulen.
     */
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
    JsonSubTypes.Type(value = KildeDto.Automatisk::class, name = "AUTOMATISK"),
    JsonSubTypes.Type(value = KildeDto.Manuell::class, name = "MANUELL"),
)
internal sealed interface KildeDto {
    data class Automatisk(
        val regelversjon: String,
    ) : KildeDto

    data class Manuell(
        val saksbehandlerIdent: String,
        val fritekstbegrunnelse: String,
    ) : KildeDto
}
