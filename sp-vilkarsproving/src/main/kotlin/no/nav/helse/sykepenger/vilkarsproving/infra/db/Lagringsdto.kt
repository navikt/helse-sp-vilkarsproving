package no.nav.helse.sykepenger.vilkarsproving.infra.db

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

/**
 * Lagringsformatet for opphav og grunnlag, uttrykt som dataklasser.
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
internal sealed interface VilkårsgrunnlagDto

/**
 * Grunnlaget er nøstet inni [OpphavDto.Automatisk], og deserialiseres derfor via [VilkårsgrunnlagDto].
 * Diskriminatoren er unik på tvers av vilkår, slik at vi ikke trenger å vite hvilket vilkår raden
 * gjelder for å lese den.
 */
internal sealed interface OpptjeningsgrunnlagDto : VilkårsgrunnlagDto {
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

/**
 * Opphavet lagres som én json-verdi som også inneholder grunnlaget, slik at en rad aldri kan havne i
 * en tilstand domenet ikke kan uttrykke — som et grunnlag på en vurdering vi ikke har gjort selv.
 *
 * Hvilket vilkår raden gjelder står i egen kolonne, og gjentas derfor ikke her.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = OpphavDto.Automatisk::class, name = "AUTOMATISK"),
    JsonSubTypes.Type(value = OpphavDto.Saksbehandler::class, name = "SAKSBEHANDLER"),
    JsonSubTypes.Type(value = OpphavDto.Infotrygd::class, name = "INFOTRYGD"),
)
internal sealed interface OpphavDto {
    data class Automatisk(
        val grunnlag: VilkårsgrunnlagDto,
        val versjonAvKildekode: String,
    ) : OpphavDto

    data class Saksbehandler(
        val ident: String,
        val fritekstbegrunnelse: String,
    ) : OpphavDto

    /** Se kommentaren på [OpptjeningsgrunnlagDto.SelvstendigNæringsdrivende] for hvorfor dette ikke er et objekt. */
    class Infotrygd : OpphavDto
}
