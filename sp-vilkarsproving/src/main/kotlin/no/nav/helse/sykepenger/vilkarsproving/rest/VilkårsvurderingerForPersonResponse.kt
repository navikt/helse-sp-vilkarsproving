@file:kotlinx.serialization.UseContextualSerialization(UUID::class, Instant::class, LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.rest

import kotlinx.serialization.Serializable
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
 * vurdering kan ha sin egen datastruktur (jf. [OpptjeningsvurderingResponse.grunnlag]) uten at
 * klienten må gjette typen ut fra innholdet.
 *
 * Verdien per nøkkel er den ene vurderingen som ble forespurt for det vilkåret — enten den
 * konkrete vurderingen man spurte om via id, eller den gjeldende (nyeste) dersom man ba om alt
 * som er vurdert for personen. `null` betyr at vilkåret ikke er vurdert (ennå).
 */
@Serializable
internal data class VilkårsvurderingerForPersonResponse(
    val opptjeningsvurdering: OpptjeningsvurderingResponse,
    // val medlemskapsvurdering: MedlemskapsvurderingResponse? = null — når medlemskapsvilkåret er implementert
)

@Serializable
internal data class OpptjeningsvurderingResponse(
    val id: UUID,
    val skjæringstidspunkt: LocalDate,
    val utfall: Utfall,
    val kodeverkkode: String,
    val grunnlag: OpptjeningsgrunnlagResponse,
    val kilde: KildeResponse,
    val vurdertTidspunkt: Instant,
)

@Serializable
internal sealed interface OpptjeningsgrunnlagResponse {
    @Serializable
    data class Arbeidstaker(
        val arbeidsforhold: List<ArbeidsforholdResponse>,
    ) : OpptjeningsgrunnlagResponse

    @Serializable
    data object SelvstendigNæringsdrivende : OpptjeningsgrunnlagResponse
}

@Serializable
internal data class ArbeidsforholdResponse(
    val orgnummer: String,
    val fom: LocalDate,
    /** Null betyr løpende ansettelsesforhold. */
    val tom: LocalDate?,
    val type: Arbeidsforhold.Arbeidsforholdtype,
)

@Serializable
internal sealed interface KildeResponse {
    @Serializable
    data class Automatisk(
        val regelversjon: String,
    ) : KildeResponse

    @Serializable
    data class Manuell(
        val saksbehandlerIdent: String,
        val fritekstbegrunnelse: String,
    ) : KildeResponse
}

/**
 * Bygger keyet respons på tvers av vilkår, én vurdering per vilkårstype: den gjeldende (nyeste)
 * dersom personen er vurdert på flere skjæringstidspunkt for samme vilkår.
 *
 * `when (vilkår)` er exhaustive over enum-en [Vilkår] — legges det til et nytt vilkår her, feiler
 * kompileringen til den nye grenen er håndtert, i stedet for å svare feil eller ufullstendig i runtime.
 */
internal fun List<Vilkårsvurdering>.tilResponse(): VilkårsvurderingerForPersonResponse {
    val gjeldendePrVilkår = groupBy { it.vilkår }.mapValues { (_, vurderinger) -> vurderinger.maxBy { it.vurdertTidspunkt } }
    return VilkårsvurderingerForPersonResponse(
        opptjeningsvurdering = gjeldendePrVilkår[Vilkår.Opptjening]!!.tilOpptjeningsvurderingResponse(),
    )
}

/**
 * Krever at [Vilkårsvurdering.vilkår] er [Vilkår.Opptjening] — kalles kun fra steder som allerede
 * vet dette (oppslag på et konkret vilkår, eller filtrert gruppering som i [tilResponse]).
 */
internal fun Vilkårsvurdering.tilOpptjeningsvurderingResponse(): OpptjeningsvurderingResponse {
    check(vilkår == Vilkår.Opptjening) { "Kan ikke bygge en opptjeningsvurdering-respons av en vurdering av $vilkår" }
    // Trygt: klasseinvarianten i Vilkårsvurdering (se init-blokken der) garanterer at
    // grunnlag.vilkår == vilkår, og vi har akkurat sjekket at vilkår er Opptjening.
    val opptjeningsgrunnlag = grunnlag as Opptjeningsgrunnlag
    return OpptjeningsvurderingResponse(
        id = id.value,
        skjæringstidspunkt = skjæringstidspunkt,
        utfall = utfall,
        kodeverkkode = kodeverkkode.name,
        grunnlag =
            when (opptjeningsgrunnlag) {
                is Opptjeningsgrunnlag.Arbeidstaker -> OpptjeningsgrunnlagResponse.Arbeidstaker(opptjeningsgrunnlag.arbeidsforhold.map { it.tilResponse() })
                Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> OpptjeningsgrunnlagResponse.SelvstendigNæringsdrivende
            },
        kilde =
            when (val kilde = kilde) {
                is Kilde.Automatisk -> KildeResponse.Automatisk(kilde.regelversjon)
                is Kilde.Manuell -> KildeResponse.Manuell(kilde.saksbehandlerIdent, kilde.fritekstbegrunnelse)
            },
        vurdertTidspunkt = vurdertTidspunkt,
    )
}

private fun Arbeidsforhold.tilResponse() =
    ArbeidsforholdResponse(
        orgnummer = orgnummer,
        fom = ansettelseperiode.start,
        tom = ansettelseperiode.endInclusive.takeUnless { it == LocalDate.MAX },
        type = type,
    )
