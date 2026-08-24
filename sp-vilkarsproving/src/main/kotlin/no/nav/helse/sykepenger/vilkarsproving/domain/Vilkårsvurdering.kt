package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Resultatet av en fullført vilkårsprøving.
 *
 * En vilkårsvurdering er immutabel og finnes aldri i en delvis tilstand: er den først konstruert,
 * er den komplett. Prosessen som ledet fram til den er modellert separat, se [Vilkårsprøving].
 *
 * Feltene her er de som gjelder for enhver vurdering, uansett vilkår og uansett hvor vurderingen
 * kommer fra — det er dét som gjør at vi kan spørre likt på tvers av dem. All variasjon ligger i
 * [Opphav] (hvilket grunnlag, og hvem som vurderte) og i [Vilkårsregel] (hva som utgjør et utfall).
 */
internal class Vilkårsvurdering private constructor(
    val id: VurderingId,
    /**
     * Prøvingen som produserte vurderingen, eller `null` for vurderinger som ikke oppsto hos oss.
     * Overførte Infotrygd-vurderinger har ingen prøving; å konstruere en syntetisk prøving for dem
     * ville påstått at vi har kjørt en prosess vi aldri har kjørt.
     */
    val prøvingId: PrøvingId?,
    val fødselsnummer: String,
    val skjæringstidspunkt: LocalDate,
    val kodeverkkode: Kodeverkkode,
    val opphav: Opphav,
    val vurdertTidspunkt: Instant,
) {
    val vilkår: Vilkår get() = opphav.vilkår
    val utfall: Utfall get() = kodeverkkode.utfall

    init {
        check(kodeverkkode.vilkår == vilkår) { "Kodeverkkode $kodeverkkode hører ikke til vilkåret $vilkår" }
        // Fra Infotrygd vet vi kun at vilkåret ble vurdert, ikke på hvilket grunnlag. Da kan vi
        // heller ikke påstå en presis kode. Motsatt vei gjelder ikke: også vi selv kan lande på en
        // generell kode.
        check(opphav !is Opphav.Infotrygd || kodeverkkode.detaljeringsgrad == Detaljeringsgrad.GENERELL) {
            "Vurderinger fra Infotrygd kan ikke bruke den presise kodeverkkoden $kodeverkkode"
        }
    }

    companion object {
        fun automatisk(
            prøvingId: PrøvingId,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            grunnlag: Vilkårsgrunnlag,
            vurdertTidspunkt: Instant,
        ): Vilkårsvurdering {
            val regel = grunnlag.vilkår.regel
            val resultat = regel.vurder(skjæringstidspunkt, grunnlag)
            return Vilkårsvurdering(
                id = VurderingId.ny(),
                prøvingId = prøvingId,
                fødselsnummer = fødselsnummer,
                skjæringstidspunkt = skjæringstidspunkt,
                kodeverkkode = resultat.kodeverkkode,
                opphav = Opphav.Automatisk(grunnlag, regel.versjon),
                vurdertTidspunkt = vurdertTidspunkt,
            )
        }

        /**
         * En vurdering gjort manuelt i saksbehandlingsløsningen vår. Saksbehandleren velger
         * kodeverkkoden selv, og det finnes ikke noe strukturert grunnlag å knytte til.
         */
        fun avSaksbehandler(
            prøvingId: PrøvingId?,
            vilkår: Vilkår,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            kodeverkkode: Kodeverkkode,
            saksbehandlerIdent: String,
            fritekstbegrunnelse: String,
            vurdertTidspunkt: Instant,
        ) = Vilkårsvurdering(
            id = VurderingId.ny(),
            prøvingId = prøvingId,
            fødselsnummer = fødselsnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            kodeverkkode = kodeverkkode,
            opphav = Opphav.Saksbehandler(vilkår, saksbehandlerIdent, fritekstbegrunnelse),
            vurdertTidspunkt = vurdertTidspunkt,
        )

        /** En vurdering overført fra Infotrygd. [kodeverkkode] må være generell, se [Opphav.Infotrygd]. */
        fun fraInfotrygd(
            vilkår: Vilkår,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            kodeverkkode: Kodeverkkode,
            vurdertTidspunkt: Instant,
        ) = Vilkårsvurdering(
            id = VurderingId.ny(),
            prøvingId = null,
            fødselsnummer = fødselsnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            kodeverkkode = kodeverkkode,
            opphav = Opphav.Infotrygd(vilkår),
            vurdertTidspunkt = vurdertTidspunkt,
        )

        /**
         * Rekonstruerer en lagret vurdering. Regelen kjøres ikke på nytt — resultatet er det som ble
         * vurdert den gangen, med den kodeversjonen som står i [opphav].
         */
        fun fraLagring(
            id: VurderingId,
            prøvingId: PrøvingId?,
            fødselsnummer: String,
            skjæringstidspunkt: LocalDate,
            kodeverkkode: Kodeverkkode,
            opphav: Opphav,
            vurdertTidspunkt: Instant,
        ) = Vilkårsvurdering(
            id = id,
            prøvingId = prøvingId,
            fødselsnummer = fødselsnummer,
            skjæringstidspunkt = skjæringstidspunkt,
            kodeverkkode = kodeverkkode,
            opphav = opphav,
            vurdertTidspunkt = vurdertTidspunkt,
        )
    }
}
