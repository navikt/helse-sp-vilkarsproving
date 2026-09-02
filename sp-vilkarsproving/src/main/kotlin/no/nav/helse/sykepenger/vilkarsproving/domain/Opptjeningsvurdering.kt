package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant
import java.time.LocalDate

internal sealed interface Opptjeningsvurdering {
    val id: OpptjeningsvurderingId
    val fødselsnummer: String
    val skjæringstidspunkt: LocalDate
    val erOk: Boolean

    data class VurdertISpeil(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        val vilkårsvurderinger: List<Vilkårsvurdering>,
    ) : Opptjeningsvurdering {
        init {
            require(vilkårsvurderinger.isNotEmpty()) { "Opptjeningsvurdering $id må ha minst én vilkårsvurdering" }
        }

        override val erOk: Boolean get() = vilkårsvurderinger.last().utfall == Utfall.Oppfylt

        val avgjørendeVilkårskode: Vilkårskode get() = vilkårsvurderinger.last().vilkårskode
    }

    data class OverførtFraInfotrygd(
        override val id: OpptjeningsvurderingId,
        override val fødselsnummer: String,
        override val skjæringstidspunkt: LocalDate,
        override val erOk: Boolean,
    ) : Opptjeningsvurdering

    companion object {
        fun automatisk(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            opptjeningsprøvingId: OpptjeningsprøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            grunnlag: Opptjeningsgrunnlag,
            vurdertTidspunkt: Instant,
        ): VurdertISpeil {
            val regel = grunnlag.regel
            val resultat = regel.vurder(skjæringstidspunkt, grunnlag)
            val vilkårsvurderinger =
                resultat.vilkårsutfall.map { ledd ->
                    Vilkårsvurdering.automatisk(opptjeningsprøvingId, ledd, grunnlag, regel.versjon, vurdertTidspunkt)
                }
            return VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, vilkårsvurderinger)
        }

        /**
         * Lager en ny vurdering av saksbehandlerens [sti]. Vilkårsvurderinger fra [forrigeVurdering] videreføres slik at
         * den nye vurderingen viser helheten, men vurderinger av vilkårskoder saksbehandleren har tatt stilling til
         * erstattes av saksbehandlerens egne.
         */
        fun avSaksbehandler(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
            forrigeVurdering: Opptjeningsvurdering? = null,
        ): VurdertISpeil {
            val overstyrteVilkårskoder = sti.map { it.vilkårskode }.toSet()
            val videreførte =
                (forrigeVurdering as? VurdertISpeil)
                    ?.vilkårsvurderinger
                    .orEmpty()
                    .filterNot { it.vilkårskode in overstyrteVilkårskoder }
                    .map { it.videreført() }
            return VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, videreførte + sti)
        }

        fun fraInfotrygd(
            id: OpptjeningsvurderingId = OpptjeningsvurderingId.ny(),
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, erOk)

        fun fraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, sti)

        fun overførtFraSpleis(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            sti: List<Vilkårsvurdering>,
        ) = VurdertISpeil(id, fødselsnummer, skjæringstidspunkt, sti)

        fun infotrygdFraLagring(
            id: OpptjeningsvurderingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            erOk: Boolean,
        ) = OverførtFraInfotrygd(id, fødselsnummer, skjæringstidspunkt, erOk)
    }
}
