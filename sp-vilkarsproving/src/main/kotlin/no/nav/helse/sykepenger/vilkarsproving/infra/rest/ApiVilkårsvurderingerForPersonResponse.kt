@file:UseContextualSerialization(UUID::class, Instant::class, LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Kilde
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Responsen for GET .../vilkarsvurderinger: én egen nøkkel per vilkårstype, slik at hver type
 * vurdering kan ha sin egen datastruktur (jf. [ApiOpptjeningsvurderingResponse.grunnlag]) uten at
 * klienten må gjette typen ut fra innholdet.
 *
 * Verdien per nøkkel er den ene vurderingen som ble forespurt for det vilkåret — enten den
 * konkrete vurderingen man spurte om via id, eller den gjeldende (nyeste) dersom man ba om alt
 * som er vurdert for personen. `null` betyr at vilkåret ikke er vurdert (ennå).
 */
@Serializable
internal data class ApiVilkårsvurderingerForPersonResponse(
    val opptjeningsvurdering: ApiOpptjeningsvurderingResponse,
)

@Serializable
internal data class ApiOpptjeningsvurderingResponse(
    val id: UUID,
    val skjæringstidspunkt: LocalDate,
    val utfall: ApiUtfallResponse,
    val kodeverkkode: String,
    val grunnlag: ApiOpptjeningsgrunnlagResponse,
    val kilde: ApiKildeResponse,
    val vurdertTidspunkt: Instant,
)

@Serializable
internal enum class ApiUtfallResponse {
    Oppfylt,
    IkkeOppfylt,
}

@Serializable
internal sealed interface ApiOpptjeningsgrunnlagResponse {
    @Serializable
    data class Arbeidstaker(
        val arbeidsforhold: List<ApiArbeidsforholdResponse>,
    ) : ApiOpptjeningsgrunnlagResponse

    @Serializable
    data object SelvstendigNæringsdrivende : ApiOpptjeningsgrunnlagResponse
}

@Serializable
internal data class ApiArbeidsforholdResponse(
    val orgnummer: String,
    val fom: LocalDate,
    /** Null betyr løpende ansettelsesforhold. */
    val tom: LocalDate?,
    val type: ApiArbeidsforholdtypeResponse,
)

@Serializable
internal enum class ApiArbeidsforholdtypeResponse {
    FORENKLET_OPPGJØRSORDNING,
    FRILANSER,
    MARITIMT,
    ORDINÆRT,
}

@Serializable
internal sealed interface ApiKildeResponse {
    @Serializable
    data class Automatisk(
        val regelversjon: String,
    ) : ApiKildeResponse

    @Serializable
    data class Manuell(
        val saksbehandlerIdent: String,
        val fritekstbegrunnelse: String,
    ) : ApiKildeResponse
}

/**
 * Krever at [vilkår] er [Vilkår.Opptjening] — kalles kun fra steder som allerede
 * vet dette (oppslag på et konkret vilkår, eller filtrert gruppering som i [tilResponse]).
 */
internal fun Vilkårsvurdering.tilApiOpptjeningsvurderingResponse(): ApiOpptjeningsvurderingResponse {
    check(vilkår == Vilkår.Opptjening) { "Kan ikke bygge en opptjeningsvurdering-respons av en vurdering av $vilkår" }
    // Trygt: klasseinvarianten i Vilkårsvurdering (se init-blokken der) garanterer at
    // grunnlag.vilkår == vilkår, og vi har akkurat sjekket at vilkår er Opptjening.
    val opptjeningsgrunnlag = grunnlag as Opptjeningsgrunnlag
    return ApiOpptjeningsvurderingResponse(
        id = id.value,
        skjæringstidspunkt = skjæringstidspunkt,
        utfall =
            when (utfall) {
                Utfall.Oppfylt -> ApiUtfallResponse.Oppfylt
                Utfall.IkkeOppfylt -> ApiUtfallResponse.IkkeOppfylt
            },
        kodeverkkode = kodeverkkode.name,
        grunnlag =
            when (opptjeningsgrunnlag) {
                is Opptjeningsgrunnlag.Arbeidstaker -> ApiOpptjeningsgrunnlagResponse.Arbeidstaker(opptjeningsgrunnlag.arbeidsforhold.map { it.tilResponse() })
                Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> ApiOpptjeningsgrunnlagResponse.SelvstendigNæringsdrivende
            },
        kilde =
            when (val kilde = kilde) {
                is Kilde.Automatisk -> ApiKildeResponse.Automatisk(kilde.regelversjon)
                is Kilde.Manuell -> ApiKildeResponse.Manuell(kilde.saksbehandlerIdent, kilde.fritekstbegrunnelse)
            },
        vurdertTidspunkt = vurdertTidspunkt,
    )
}

private fun Arbeidsforhold.tilResponse() =
    ApiArbeidsforholdResponse(
        orgnummer = orgnummer,
        fom = ansettelseperiode.start,
        tom = ansettelseperiode.endInclusive.takeUnless { it == LocalDate.MAX },
        type =
            when (type) {
                Arbeidsforhold.Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING -> ApiArbeidsforholdtypeResponse.FORENKLET_OPPGJØRSORDNING
                Arbeidsforhold.Arbeidsforholdtype.FRILANSER -> ApiArbeidsforholdtypeResponse.FRILANSER
                Arbeidsforhold.Arbeidsforholdtype.MARITIMT -> ApiArbeidsforholdtypeResponse.MARITIMT
                Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT -> ApiArbeidsforholdtypeResponse.ORDINÆRT
            },
    )
