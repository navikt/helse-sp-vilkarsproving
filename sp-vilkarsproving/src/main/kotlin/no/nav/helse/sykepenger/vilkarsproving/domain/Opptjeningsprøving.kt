package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Prosessen som leder fram til en [Kravvurdering.VurdertISpeil] av opptjeningskravet.
 *
 * Prøvingen eier livssyklusen — hva vi venter på, hvor lenge, og om vi er ferdige — mens selve
 * vurderingen er resultatet den produserer. En prøving i denne appen *er* en automatisk prøving: det
 * finnes ingen prøving for en manuell vurdering eller en vurdering overført fra Spleis eller Infotrygd,
 * se [Vurderingskilde].
 *
 * Prøvingen holder ikke på innhentede fakta. Det er ikke nødvendig så lenge den venter på ett grunnlag om
 * gangen: kommer svaret, konstrueres grunnlaget og vurderingen i samme operasjon. Skal kravet senere
 * vente på flere uavhengige svar må vi legge til et arbeidsminne her.
 */
internal class Opptjeningsprøving private constructor(
    val id: OpptjeningsprøvingId,
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
    fun motta(grunnlag: Opptjeningsgrunnlag): Kravvurdering.VurdertISpeil {
        val venter =
            tilstand as? Tilstand.VenterPåGrunnlag
                ?: error("Prøving $id venter ikke på grunnlag, men er i tilstand $tilstand")
        check(grunnlag.besvarer == venter.behov) {
            "Prøving $id venter på ${venter.behov}, men fikk grunnlag som besvarer ${grunnlag.besvarer}"
        }
        return fullfør(grunnlag)
    }

    private fun fullfør(grunnlag: Opptjeningsgrunnlag): Kravvurdering.VurdertISpeil {
        val vurdering =
            Kravvurdering.automatisk(
                opptjeningsprøvingId = id,
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
        val prøving: Opptjeningsprøving,
        val vurdering: Kravvurdering.VurdertISpeil?,
    )

    companion object {
        /**
         * Starter en prøving av opptjeningskravet.
         *
         * Arbeidstakere må vi hente arbeidsforhold for, mens selvstendig næringsdrivende kan vurderes
         * med en gang.
         */
        fun start(
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            arbeidssituasjon: Arbeidssituasjon,
        ): Påbegynt {
            val umiddelbartGrunnlag =
                when (arbeidssituasjon) {
                    Arbeidssituasjon.Arbeidstaker -> null
                    Arbeidssituasjon.SelvstendigNæringsdrivende -> Opptjeningsgrunnlag.SelvstendigNæringsdrivende
                }
            val prøving =
                Opptjeningsprøving(
                    id = OpptjeningsprøvingId.ny(),
                    fødselsnummer = fødselsnummer,
                    skjæringstidspunkt = skjæringstidspunkt,
                    startet = Instant.now(),
                    tilstand = Tilstand.Startet,
                )
            val vurdering =
                when (umiddelbartGrunnlag) {
                    null -> {
                        prøving.tilstand = Tilstand.VenterPåGrunnlag(Grunnlagsbehov.Arbeidsforhold)
                        null
                    }

                    else -> prøving.fullfør(umiddelbartGrunnlag)
                }
            return Påbegynt(prøving, vurdering)
        }

        fun fraLagring(
            id: OpptjeningsprøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            startet: Instant,
            tilstand: Tilstand,
        ) = Opptjeningsprøving(id, fødselsnummer, skjæringstidspunkt, startet, tilstand)
    }
}
