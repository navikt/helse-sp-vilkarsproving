package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Prosessen som leder fram til en [Kravvurdering.Vurdert].
 *
 * Prøvingen eier livssyklusen — hva vi venter på, hvor lenge, og om vi er ferdige — mens selve
 * vurderingen er resultatet den produserer. En prøving i denne appen *er* en automatisk prøving: det
 * finnes ingen prøving for en manuell vurdering eller en vurdering overført fra Spleis eller Infotrygd,
 * se [Vurderingskilde].
 *
 * Livssyklusen er den samme for alle krav, og ligger derfor her. Det kravspesifikke — hvilket grunnlag
 * som må innhentes, og hva som utgjør et utfall — ligger i [Vilkårsgrunnlag] og [Kravregel]. Hvert krav
 * har en egen startfunksjon, se `Opptjeningsprøving`.
 *
 * Prøvingen holder ikke på innhentede fakta. Det er ikke nødvendig så lenge den venter på ett grunnlag om
 * gangen: kommer svaret, konstrueres grunnlaget og vurderingen i samme operasjon. Skal et krav senere
 * vente på flere uavhengige svar må vi legge til et arbeidsminne her.
 */
internal class Kravprøving private constructor(
    val id: PrøvingId,
    val krav: Krav,
    val fødselsnummer: String,
    val skjæringstidspunkt: LocalDate,
    val startet: Instant,
    tilstand: Tilstand,
) {
    var tilstand: Tilstand = tilstand
        private set

    val erAvsluttet get() = tilstand is Tilstand.Fullført

    val uteståendeBehov get() = (tilstand as? Tilstand.VenterPåGrunnlag)?.behov

    sealed interface Tilstand {
        data object Startet : Tilstand

        data class VenterPåGrunnlag(
            val behov: Grunnlagsbehov,
        ) : Tilstand

        data class Fullført(
            val kravvurderingId: KravvurderingId,
        ) : Tilstand
    }

    /**
     * Tar imot grunnlaget prøvingen venter på og produserer kravvurderingen.
     * Vurderingen og den oppdaterte prøvingen må lagres i samme transaksjon.
     */
    fun motta(grunnlag: Vilkårsgrunnlag): Kravvurdering.Vurdert {
        val venter =
            tilstand as? Tilstand.VenterPåGrunnlag
                ?: error("Prøving $id venter ikke på grunnlag, men er i tilstand $tilstand")
        check(grunnlag.besvarer == venter.behov) {
            "Prøving $id venter på ${venter.behov}, men fikk grunnlag som besvarer ${grunnlag.besvarer}"
        }
        return fullfør(grunnlag)
    }

    private fun fullfør(grunnlag: Vilkårsgrunnlag): Kravvurdering.Vurdert {
        check(grunnlag.krav == krav) { "Prøving $id gjelder $krav, men fikk grunnlag for ${grunnlag.krav}" }
        val vurdering =
            Kravvurdering.automatisk(
                prøvingId = id,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                grunnlag = grunnlag,
                vurdertTidspunkt = Instant.now(),
            )
        tilstand = Tilstand.Fullført(vurdering.id)
        return vurdering
    }

    /** En påbegynt prøving. [vurdering] er satt dersom prøvingen kunne fullføres uten å innhente noe. */
    data class Påbegynt(
        val prøving: Kravprøving,
        val vurdering: Kravvurdering.Vurdert?,
    )

    companion object {
        /**
         * Starter en prøving. [umiddelbartGrunnlag] settes av krav som kan vurderes uten å hente noe
         * utenfra; ellers venter prøvingen på [behov].
         */
        fun start(
            krav: Krav,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            behov: Grunnlagsbehov,
            umiddelbartGrunnlag: Vilkårsgrunnlag?,
        ): Påbegynt {
            val prøving =
                Kravprøving(
                    id = PrøvingId.ny(),
                    krav = krav,
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    startet = Instant.now(),
                    tilstand = Tilstand.Startet,
                )
            val vurdering =
                when (umiddelbartGrunnlag) {
                    null -> {
                        prøving.tilstand = Tilstand.VenterPåGrunnlag(behov)
                        null
                    }

                    else -> prøving.fullfør(umiddelbartGrunnlag)
                }
            return Påbegynt(prøving, vurdering)
        }

        fun fraLagring(
            id: PrøvingId,
            krav: Krav,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            startet: Instant,
            tilstand: Tilstand,
        ) = Kravprøving(id, krav, fødselsnummer, skjæringstidspunkt, startet, tilstand)
    }
}
