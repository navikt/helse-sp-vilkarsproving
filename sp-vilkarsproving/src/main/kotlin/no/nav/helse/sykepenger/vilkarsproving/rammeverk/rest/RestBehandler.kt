package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang

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

    fun openApi(config: RouteConfig) {}

    fun operationIdBasertPåKlassenavn(): String =
        this::class
            .simpleName
            ?.replaceFirstChar { it.lowercaseChar() }
            ?: this::class.java.name
}

inline fun <reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle> RestBehandler<ROLLE>.openApiUtenRequestBody(config: RouteConfig) {
    config.operationId = operationIdBasertPåKlassenavn()
    config.response {
        val harResponsBody = RESPONSE::class != Unit::class
        code(if (harResponsBody) HttpStatusCode.OK else HttpStatusCode.NoContent) {
            description = "Vellykket svar"
            if (harResponsBody) {
                body<RESPONSE>()
            }
        }
        default {
            description = "Svar ved feil"
            body<ProblemDetails> {
                mediaTypes = setOf(ContentType.Application.ProblemJson)
            }
        }
    }
    config.tags = setOf(tag)
    openApi(config)
}

/** Som [openApiUtenRequestBody], men dokumenterer i tillegg request-bodyen. */
inline fun <reified REQUEST, reified RESPONSE, ERROR : ApiErrorCode, ROLLE : Brukerrolle> RestBehandler<ROLLE>.openApiMedRequestBody(
    config: RouteConfig,
) {
    config.request {
        body<REQUEST>()
    }
    openApiUtenRequestBody<RESPONSE, ERROR, ROLLE>(config)
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
