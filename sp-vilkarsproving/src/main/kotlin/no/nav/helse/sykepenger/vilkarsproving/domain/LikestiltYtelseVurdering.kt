package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant

/**
 * Saksbehandlerens svar på spørsmålene om § 8-2 andre ledd. Svarene gir én vilkårsvurdering per punktum som faktisk
 * ble vurdert:
 *
 * - første punktum ([Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER]) vurderes alltid
 * - andre punktum ([Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP]) vurderes bare når første punktum er
 *   oppfylt og [Opptjeningsaktivitet.FORELDREPENGER] er blant ytelsene
 * - tredje punktum ([Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER]) vurderes bare når
 *   andre punktum ikke er oppfylt
 */
internal data class SvarPåLikestiltYtelse(
    val sammenlagtMinst4Uker: Boolean,
    val ytelser: List<Opptjeningsaktivitet>,
    val foreldrepengerOpptjentPåAap: Boolean?,
    val yrkesaktivMinst4UkerFørUttak: Boolean?,
)

internal sealed interface LikestiltYtelseVurderingResultat {
    data class Vurdert(
        val vilkårsvurderinger: List<Vilkårsvurdering>,
    ) : LikestiltYtelseVurderingResultat

    data class Ugyldig(
        val ugyldighet: UgyldigSvarPåLikestiltYtelse,
    ) : LikestiltYtelseVurderingResultat
}

internal enum class UgyldigSvarPåLikestiltYtelse {
    YTELSER_MANGLER,
    YTELSER_UTEN_SAMMENLAGT_OPPTJENING,
    FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER,
    FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER,
    YRKESAKTIV_FØR_UTTAK_MANGLER,
    YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP,
}

internal object LikestiltYtelseVurdering {
    /**
     * Alle vurderingene får samme [vurdertTidspunkt] og samme [Vurderingskilde.Saksbehandler] (med
     * [SvarPåLikestiltYtelse.ytelser]), slik at de kan vises som én saksbehandlerhandling.
     */
    fun vurder(
        svar: SvarPåLikestiltYtelse,
        saksbehandlerIdent: String,
        fritekstbegrunnelse: String,
        journalpostId: List<String>,
        vurdertTidspunkt: Instant = Instant.now(),
    ): LikestiltYtelseVurderingResultat {
        svar.ugyldighet()?.let { return LikestiltYtelseVurderingResultat.Ugyldig(it) }

        val kilde = Vurderingskilde.Saksbehandler(saksbehandlerIdent, fritekstbegrunnelse, journalpostId, svar.ytelser)

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
                add(vurdering(Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER, svar.sammenlagtMinst4Uker))
                val opptjentPåAap = svar.foreldrepengerOpptjentPåAap ?: return@buildList
                add(vurdering(Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP, !opptjentPåAap))
                val yrkesaktiv = svar.yrkesaktivMinst4UkerFørUttak ?: return@buildList
                add(vurdering(Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER, yrkesaktiv))
            }
        return LikestiltYtelseVurderingResultat.Vurdert(vurderinger)
    }

    // Etter valideringen er et svar satt hvis og bare hvis punktumet skal vurderes
    private fun SvarPåLikestiltYtelse.ugyldighet(): UgyldigSvarPåLikestiltYtelse? {
        val harForeldrepenger = Opptjeningsaktivitet.FORELDREPENGER in ytelser
        return when {
            sammenlagtMinst4Uker && ytelser.isEmpty() -> UgyldigSvarPåLikestiltYtelse.YTELSER_MANGLER
            !sammenlagtMinst4Uker && ytelser.isNotEmpty() -> UgyldigSvarPåLikestiltYtelse.YTELSER_UTEN_SAMMENLAGT_OPPTJENING
            harForeldrepenger && foreldrepengerOpptjentPåAap == null -> UgyldigSvarPåLikestiltYtelse.FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER
            !harForeldrepenger && foreldrepengerOpptjentPåAap != null ->
                UgyldigSvarPåLikestiltYtelse.FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER
            foreldrepengerOpptjentPåAap == true && yrkesaktivMinst4UkerFørUttak == null ->
                UgyldigSvarPåLikestiltYtelse.YRKESAKTIV_FØR_UTTAK_MANGLER
            foreldrepengerOpptjentPåAap != true && yrkesaktivMinst4UkerFørUttak != null ->
                UgyldigSvarPåLikestiltYtelse.YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP
            else -> null
        }
    }
}
