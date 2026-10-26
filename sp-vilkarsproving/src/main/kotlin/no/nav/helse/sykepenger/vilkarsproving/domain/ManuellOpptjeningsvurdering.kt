package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

/**
 * Saksbehandlerens svar i det samlede skjemaet for opptjening etter § 8-2. Svarene gir én vilkårsvurdering for første
 * ledd og én per punktum i andre ledd som faktisk ble vurdert:
 *
 * - bare [Opptjeningsaktivitet.ARBEID]: første ledd oppfylt, andre ledd vurderes ikke (scenario A)
 * - minst én likestilt ytelse: første ledd ikke oppfylt, og andre ledd vurderes punktum for punktum (scenario B, C og E)
 * - ikke minst 4 uker: første ledd og første punktum i andre ledd ikke oppfylt (scenario D)
 *
 * Scenarioene er beskrevet i docs/opptjening-8-2-kodeverk.md.
 */
internal data class SvarPåOpptjening(
    val minst4Uker: Boolean,
    val opptjeningsaktiviteter: Set<Opptjeningsaktivitet>,
    val foreldrepengerOpptjentPåAap: Boolean?,
    val yrkesaktivMinst4UkerFørUttak: Boolean?,
) {
    val harLikestiltYtelse: Boolean get() = opptjeningsaktiviteter.any { it.erLikestiltYtelse }
}

internal sealed interface ManuellOpptjeningsvurderingResultat {
    data class Vurdert(
        val vilkårsvurderinger: List<Vilkårsvurdering>,
    ) : ManuellOpptjeningsvurderingResultat

    data class Ugyldig(
        val ugyldighet: UgyldigSvarPåOpptjening,
    ) : ManuellOpptjeningsvurderingResultat
}

internal enum class UgyldigSvarPåOpptjening {
    OPPTJENINGSAKTIVITETER_MANGLER,
    OPPTJENINGSAKTIVITETER_UTEN_MINST_4_UKER,
    FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER,
    FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER,
    YRKESAKTIV_FØR_UTTAK_MANGLER,
    YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP,
}

internal object ManuellOpptjeningsvurdering {
    /**
     * Alle vurderingene får samme [vurdertTidspunkt] og samme [Vurderingskilde.Saksbehandler] (med
     * [SvarPåOpptjening.opptjeningsaktiviteter]), slik at de kan vises som én saksbehandlerhandling.
     */
    fun vurder(
        svar: SvarPåOpptjening,
        saksbehandlerIdent: String,
        fritekstbegrunnelse: String,
        journalpostId: List<String>,
        vurdertTidspunkt: Instant = Instant.now(),
    ): ManuellOpptjeningsvurderingResultat {
        svar.ugyldighet()?.let { return ManuellOpptjeningsvurderingResultat.Ugyldig(it) }

        val kilde =
            Vurderingskilde.Saksbehandler(
                ident = saksbehandlerIdent,
                fritekstbegrunnelse = fritekstbegrunnelse,
                journalpostId = journalpostId,
                opptjeningsaktiviteter = svar.opptjeningsaktiviteter.sortedBy { it.ordinal },
            )

        fun vurdering(
            vilkårskode: Vilkårskode,
            oppfylt: Boolean,
        ) = Vilkårsvurdering(
            id = VilkårsvurderingId.ny(),
            vilkårskode = vilkårskode,
            utfall = if (oppfylt) Utfall.Oppfylt else Utfall.IkkeOppfylt,
            vurdertTidspunkt = vurdertTidspunkt,
            kilde = kilde,
            vurderingskilde = Opptjeningsvurderingskilde.VURDERT_I_SP_VILKARSPROVING,
            lovreferanse = vilkårskode.lovreferanse,
        )

        val vurderinger =
            buildList {
                val førsteLeddOppfylt = svar.minst4Uker && !svar.harLikestiltYtelse
                add(vurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, førsteLeddOppfylt))
                if (førsteLeddOppfylt) return@buildList
                add(vurdering(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER, svar.minst4Uker))
                val opptjentPåAap = svar.foreldrepengerOpptjentPåAap ?: return@buildList
                add(vurdering(Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP, !opptjentPåAap))
                val yrkesaktiv = svar.yrkesaktivMinst4UkerFørUttak ?: return@buildList
                add(vurdering(Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER, yrkesaktiv))
            }
        return ManuellOpptjeningsvurderingResultat.Vurdert(vurderinger)
    }

    // Etter valideringen er et svar satt hvis og bare hvis punktumet skal vurderes
    private fun SvarPåOpptjening.ugyldighet(): UgyldigSvarPåOpptjening? {
        val harForeldrepenger = Opptjeningsaktivitet.FORELDREPENGER in opptjeningsaktiviteter
        return when {
            minst4Uker && opptjeningsaktiviteter.isEmpty() -> UgyldigSvarPåOpptjening.OPPTJENINGSAKTIVITETER_MANGLER
            !minst4Uker && opptjeningsaktiviteter.isNotEmpty() -> UgyldigSvarPåOpptjening.OPPTJENINGSAKTIVITETER_UTEN_MINST_4_UKER
            harForeldrepenger && foreldrepengerOpptjentPåAap == null -> UgyldigSvarPåOpptjening.FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER
            !harForeldrepenger && foreldrepengerOpptjentPåAap != null ->
                UgyldigSvarPåOpptjening.FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER
            foreldrepengerOpptjentPåAap == true && yrkesaktivMinst4UkerFørUttak == null -> UgyldigSvarPåOpptjening.YRKESAKTIV_FØR_UTTAK_MANGLER
            foreldrepengerOpptjentPåAap != true && yrkesaktivMinst4UkerFørUttak != null ->
                UgyldigSvarPåOpptjening.YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP
            else -> null
        }
    }
}
