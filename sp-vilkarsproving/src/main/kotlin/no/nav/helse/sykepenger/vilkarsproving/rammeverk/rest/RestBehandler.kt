package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchemaInference
import io.ktor.openapi.Operation
import io.ktor.openapi.Parameters
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.jsonSchema
import io.ktor.resources.serialization.ResourcesFormat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Kontrakten alle REST-endepunkter i appen implementerer. Deny-by-default: [påkrevdTilgang] må
 * alltid deklareres eksplisitt — det finnes ingen "ingen tilgang kreves"-variant.
 */
interface RestBehandler<ROLLE : Brukerrolle> {
    /** Tilgangen (Les/Skriv) som kreves for å kalle dette endepunktet. Deny-by-default. */
    val påkrevdTilgang: Tilgang

    /** Ytterligere brukerroller som kreves, utover [påkrevdTilgang]. Tomt sett = ingen ekstra krav. */
    val påkrevdeBrukerroller: Set<ROLLE> get() = emptySet()

    /** Kort tag brukt til gruppering i generert OpenAPI-dokumentasjon. */
    val tag: String

    fun openApi(operation: Operation.Builder) {}

    fun operationIdBasertPåKlassenavn(): String =
        this::class
            .simpleName
            ?.replaceFirstChar { it.lowercaseChar() }
            ?: this::class.java.name
}

inline fun <reified RESOURCE : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle> RestBehandler<ROLLE>.openApiUtenRequestBody(
    operation: Operation.Builder,
) {
    operation.operationId = operationIdBasertPåKlassenavn()
    operation.tag(tag)
    operation.parameters { dokumenterResourceParametere(operation, serializer<RESOURCE>(), typeOf<RESOURCE>()) }
    operation.responses {
        if (RESPONSE::class != Unit::class) {
            HttpStatusCode.OK {
                description = "Vellykket svar"
                schema = operation.buildSchema(typeOf<RESPONSE>())
            }
        } else {
            HttpStatusCode.NoContent { description = "Vellykket svar" }
        }
        default {
            description = "Svar ved feil"
            ContentType.Application.ProblemJson { schema = operation.jsonSchema<ProblemDetails>() }
        }
    }
    openApi(operation)
}

/** Som [openApiUtenRequestBody], men dokumenterer i tillegg request-bodyen. */
inline fun <reified RESOURCE : Any, reified REQUEST : Any, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle> RestBehandler<ROLLE>.openApiMedRequestBody(
    operation: Operation.Builder,
) {
    operation.requestBody {
        required = true
        schema = operation.jsonSchema<REQUEST>()
    }
    openApiUtenRequestBody<RESOURCE, RESPONSE, ERROR, ROLLE>(operation)
}

/**
 * Ktor legger selv til parameterne fra `@Resource`-klassen, men bare med navn. Her får de typen
 * (f.eks. `format: uuid`) fra skjemaet til resource-klassen.
 */
@PublishedApi
internal fun Parameters.Builder.dokumenterResourceParametere(
    skjemainferens: JsonSchemaInference,
    serializer: KSerializer<*>,
    type: KType,
) {
    val stimønster = ResourcesFormat().encodeToPathPattern(serializer)
    val descriptor = serializer.descriptor
    val egenskaper = skjemainferens.buildSchema(type).properties.orEmpty()
    for (indeks in 0 until descriptor.elementsCount) {
        val navn = descriptor.getElementName(indeks)
        val skjema = (egenskaper[navn] as? ReferenceOr.Value)?.value
        if ("{$navn}" in stimønster || "{$navn?}" in stimønster) {
            path(navn) { this.schema = skjema }
        } else {
            query(navn) {
                this.schema = skjema
                required = !descriptor.isElementOptional(indeks) && !descriptor.getElementDescriptor(indeks).isNullable
            }
        }
    }
}

interface GetBehandler<RESOURCE, RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> : RestBehandler<ROLLE> {
    fun behandle(
        resource: RESOURCE,
        kallKontekst: KallKontekst<TRANSAKSJON, ROLLE>,
    ): RestResponse<RESPONSE, ERROR>
}

interface PostBehandler<RESOURCE, REQUEST, RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle, TRANSAKSJON> : RestBehandler<ROLLE> {
    fun behandle(
        resource: RESOURCE,
        request: REQUEST,
        kallKontekst: KallKontekst<TRANSAKSJON, ROLLE>,
    ): RestResponse<RESPONSE, ERROR>
}
