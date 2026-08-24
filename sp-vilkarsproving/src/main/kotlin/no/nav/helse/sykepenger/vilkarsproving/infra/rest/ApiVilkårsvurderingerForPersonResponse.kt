@file:UseContextualSerialization(UUID::class, Instant::class, LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Opphav
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsregel
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serializable
internal data class ApiVilkårsvurderingerForPersonResponse(
    val opptjeningsvurdering: ApiOpptjeningsvurdering,
)

@Serializable
internal data class ApiOpptjeningsvurdering(
    val id: UUID,
    val skjæringstidspunkt: LocalDate,
    val rettTilSykepenger: Boolean,
    val kodeverkkode: String,
    val opphav: ApiOpphav,
    val vurdertTidspunkt: Instant,
)

@Serializable
internal data class ApiPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

@Serializable
internal sealed interface ApiOpptjeningsgrunnlag {
    @Serializable
    data class Arbeidstaker(
        val arbeidsforhold: List<ApiArbeidsforhold>,
        val opptjeningsperiode: ApiPeriode?,
        val opptjeningsdager: Int,
    ) : ApiOpptjeningsgrunnlag

    @Serializable
    data object InfotrygdVurdert : ApiOpptjeningsgrunnlag

    @Serializable
    data object SelvstendigNæringsdrivende : ApiOpptjeningsgrunnlag
}

@Serializable
internal data class ApiArbeidsforhold(
    val organisasjonsnummer: String,
    val fom: LocalDate,
    /** Null betyr løpende ansettelsesforhold. */
    val tom: LocalDate?,
    val type: ApiArbeidsforholdtype,
)

@Serializable
internal enum class ApiArbeidsforholdtype {
    FORENKLET_OPPGJØRSORDNING,
    FRILANSER,
    MARITIMT,
    ORDINÆRT,
}

@Serializable
internal sealed interface ApiOpphav {
    @Serializable
    data class Automatisk(
        val grunnlag: ApiOpptjeningsgrunnlag,
        val versjonAvKildekode: String,
    ) : ApiOpphav

    @Serializable
    data class Saksbehandler(
        val ident: String,
        val fritekstbegrunnelse: String,
    ) : ApiOpphav

    @Serializable
    object Infotrygd : ApiOpphav
}

/**
 * Krever at [vilkår] er [Vilkår.Opptjening] — kalles kun fra steder som allerede
 * vet dette (oppslag på et konkret vilkår, eller filtrert gruppering som i [tilResponse]).
 */
internal fun Vilkårsvurdering.tilApiOpptjeningsvurderingResponse(): ApiOpptjeningsvurdering {
    check(vilkår == Vilkår.Opptjening) { "Kan ikke bygge en opptjeningsvurdering-respons av en vurdering av $vilkår" }
    return ApiOpptjeningsvurdering(
        id = id.value,
        skjæringstidspunkt = skjæringstidspunkt,
        rettTilSykepenger =
            when (utfall) {
                Utfall.Oppfylt -> true
                Utfall.IkkeOppfylt -> false
            },
        kodeverkkode = kodeverkkode.name,
        opphav =
            when (val opphav = opphav) {
                is Opphav.Automatisk ->
                    ApiOpphav.Automatisk(
                        // Trygt: klasseinvarianten i Vilkårsvurdering garanterer at grunnlaget hører
                        // til vurderingens vilkår, og vi har akkurat sjekket at det er Opptjening.
                        grunnlag = (opphav.grunnlag as Opptjeningsgrunnlag).tilResponse(skjæringstidspunkt),
                        versjonAvKildekode = opphav.versjonAvKildekode,
                    )

                is Opphav.Saksbehandler -> ApiOpphav.Saksbehandler(opphav.ident, opphav.fritekstbegrunnelse)
                is Opphav.Infotrygd -> ApiOpphav.Infotrygd
            },
        vurdertTidspunkt = vurdertTidspunkt,
    )
}

/**
 * Opptjeningsperioden og antall opptjeningsdager er ikke lagret; de utledes ved å kjøre regelen på
 * nytt på det lagrede grunnlaget.
 */
private fun Opptjeningsgrunnlag.tilResponse(skjæringstidspunkt: LocalDate): ApiOpptjeningsgrunnlag =
    when (this) {
        is Opptjeningsgrunnlag.Arbeidstaker -> {
            val resultat = Opptjeningsregel.vurder(skjæringstidspunkt, this)
            ApiOpptjeningsgrunnlag.Arbeidstaker(
                arbeidsforhold = arbeidsforhold.map { it.tilResponse() },
                opptjeningsperiode = resultat.opptjeningsperiode?.tilResponse(),
                // Uten en opptjeningsperiode fram til skjæringstidspunktet er det null dager opptjening.
                opptjeningsdager = resultat.opptjeningsdager ?: 0,
            )
        }

        Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> ApiOpptjeningsgrunnlag.SelvstendigNæringsdrivende
    }

private fun no.nav.helse.hendelser.Periode.tilResponse() = ApiPeriode(fom = start, tom = endInclusive)

private fun Arbeidsforhold.tilResponse() =
    ApiArbeidsforhold(
        organisasjonsnummer = orgnummer,
        fom = ansettelseperiode.start,
        tom = ansettelseperiode.endInclusive.takeUnless { it == LocalDate.MAX },
        type =
            when (type) {
                Arbeidsforhold.Arbeidsforholdtype.FORENKLET_OPPGJØRSORDNING -> ApiArbeidsforholdtype.FORENKLET_OPPGJØRSORDNING
                Arbeidsforhold.Arbeidsforholdtype.FRILANSER -> ApiArbeidsforholdtype.FRILANSER
                Arbeidsforhold.Arbeidsforholdtype.MARITIMT -> ApiArbeidsforholdtype.MARITIMT
                Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT -> ApiArbeidsforholdtype.ORDINÆRT
            },
    )
