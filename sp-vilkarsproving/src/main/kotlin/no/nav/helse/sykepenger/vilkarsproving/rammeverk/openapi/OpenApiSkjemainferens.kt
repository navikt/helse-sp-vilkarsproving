package no.nav.helse.sykepenger.vilkarsproving.rammeverk.openapi

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonSchemaInference
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.reflect.ReflectionJsonSchemaInference
import io.ktor.openapi.reflect.SchemaReflectionAdapter
import java.math.BigDecimal
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Skjemaene utledes med refleksjon fra de samme klassene og Jackson-annotasjonene som styrer selve
 * (de)serialiseringen, slik at spec-en og wire-formatet har én felles kilde.
 */
val openApiSkjemainferens: JsonSchemaInference =
    ReflectionJsonSchemaInference(JacksonSkjemaAdapter).let { inferens ->
        JsonSchemaInference { type -> inferens.buildSchema(type).tilpassetJackson() }
    }

private object JacksonSkjemaAdapter : SchemaReflectionAdapter {
    override fun getDiscriminatorProperty(kClass: KClass<*>): String =
        kClass
            .findAnnotation<JsonTypeInfo>()
            ?.property
            ?.takeIf { it.isNotEmpty() }
            ?: error("Sealed type ${kClass.qualifiedName} mangler @JsonTypeInfo med property")

    override fun getDiscriminatorValue(
        kClass: KClass<*>,
        subclass: KClass<*>,
    ): String =
        kClass
            .findAnnotation<JsonSubTypes>()
            ?.value
            ?.firstOrNull { it.value == subclass }
            ?.name
            ?.takeIf { it.isNotEmpty() }
            ?: error("${subclass.qualifiedName} mangler navn i @JsonSubTypes på ${kClass.qualifiedName}")
}

private val skjemaForSkalartyper =
    mapOf(
        UUID::class.qualifiedName to JsonSchema(type = JsonType.STRING, format = "uuid"),
        BigDecimal::class.qualifiedName to JsonSchema(type = JsonType.STRING, format = "bigdecimal"),
    )

/**
 * Retter to mangler i [ReflectionJsonSchemaInference]:
 * - JDK-typer den ikke kjenner (f.eks. [UUID]) beskrives som objekter med de interne feltene sine.
 * - Nullbarhet blir borte for tall og boolske verdier, og havner i den delte komponenten for
 *   objekttyper. Inferensen markerer nøyaktig de ikke-nullbare egenskapene som required, så de som
 *   mangler der er de nullbare.
 */
private fun JsonSchema.tilpassetJackson(): JsonSchema {
    skjemaForSkalartyper[title]?.let { return it }
    val påkrevde = required.orEmpty().toSet()
    return copy(
        properties =
            properties?.mapValues { (navn, skjema) ->
                skjema.tilpassetJackson().let { if (navn in påkrevde) it else it.somNullbar() }
            },
        items = items?.tilpassetJackson(),
        oneOf = oneOf?.map { it.tilpassetJackson() },
        anyOf = anyOf?.map { it.tilpassetJackson() },
        allOf = allOf?.map { it.tilpassetJackson() },
        additionalProperties =
            (additionalProperties as? AdditionalProperties.PSchema)
                ?.let { AdditionalProperties.PSchema(it.value.tilpassetJackson()) }
                ?: additionalProperties,
    )
}

private fun ReferenceOr<JsonSchema>.tilpassetJackson(): ReferenceOr<JsonSchema> =
    when (this) {
        is ReferenceOr.Reference -> this
        is ReferenceOr.Value -> ReferenceOr.Value(value.tilpassetJackson())
    }

private val nullskjema = ReferenceOr.Value(JsonSchema(type = JsonType.NULL))

private fun ReferenceOr<JsonSchema>.somNullbar(): ReferenceOr<JsonSchema> {
    val skjema = (this as? ReferenceOr.Value)?.value ?: return this
    if (skjema.title != null) {
        // Skjemaer med tittel blir delte komponenter; null må ligge ved bruksstedet, ikke i komponenten.
        return ReferenceOr.Value(JsonSchema(oneOf = listOf(ReferenceOr.Value(skjema.utenNull()), nullskjema)))
    }
    val type = skjema.type as? JsonType ?: return this
    return ReferenceOr.Value(skjema.copy(type = JsonSchema.SchemaType.AnyOf(listOf(type, JsonType.NULL))))
}

private fun JsonSchema.utenNull(): JsonSchema {
    val typer = (type as? JsonSchema.SchemaType.AnyOf)?.types ?: return this
    return copy(type = typer.filter { it != JsonType.NULL }.singleOrNull() ?: JsonSchema.SchemaType.AnyOf(typer - JsonType.NULL))
}
