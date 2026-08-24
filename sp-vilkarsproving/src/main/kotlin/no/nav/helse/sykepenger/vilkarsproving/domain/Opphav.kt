package no.nav.helse.sykepenger.vilkarsproving.domain

/**
 * Hvordan en [Vilkårsvurdering] ble til.
 *
 * Opphavet eier alt som varierer mellom måtene en vurdering kan oppstå på: hvilket grunnlag den
 * bygger på, og hvem som står bak. Det er nettopp her de tre kildene våre er forskjellige — vi
 * kjenner ulike deler av historien avhengig av hvor vurderingen kommer fra:
 *
 * | Opphav          | Grunnlag | Hvem vurderte              |
 * |-----------------|----------|----------------------------|
 * | [Automatisk]    | ja       | oss, på en gitt kodeversjon |
 * | [Saksbehandler] | nei      | en navngitt saksbehandler  |
 * | [Infotrygd]     | nei      | ukjent, men manuelt        |
 *
 * Fordi variasjonen samles her, kan resten av [Vilkårsvurdering] være lik for alle vurderinger, og
 * vi kan spørre likt på tvers av dem. Et nytt opphav legges til som en ny variant, uten at noe
 * eksisterende felt må gjøres nullbart.
 */
internal sealed interface Opphav {
    val vilkår: Vilkår

    /**
     * Vurdert maskinelt av oss. Det eneste opphavet som har grunnlagsdata, fordi det er det eneste
     * tilfellet der vi selv hentet inn fakta og kjørte en [Vilkårsregel] på dem.
     */
    data class Automatisk(
        val grunnlag: Vilkårsgrunnlag,
        val versjonAvKildekode: String,
    ) : Opphav {
        override val vilkår get() = grunnlag.vilkår
    }

    /**
     * Vurdert manuelt i saksbehandlingsløsningen vår. Vi kjenner både saksbehandleren og nøyaktig
     * hvilken kodeverkkode det ble vurdert til, men det finnes ikke noe strukturert grunnlag —
     * vurderingen ble gjort på et menneskelig skjønn som kun er dokumentert i [fritekstbegrunnelse].
     */
    data class Saksbehandler(
        override val vilkår: Vilkår,
        val ident: String,
        val fritekstbegrunnelse: String,
    ) : Opphav

    /**
     * Overført fra Infotrygd. Vurderingen ble gjort manuelt der, men vi kjenner verken grunnlaget
     * eller hvem som gjorde den — og kodeverkkoden er derfor alltid en generell kode, se
     * [Detaljeringsgrad] og invarianten i [Vilkårsvurdering].
     *
     * Dette er bevisst et eget opphav og ikke en [Saksbehandler] med en «ukjent» ident: en
     * plassholderverdi i identfeltet ville lekket ut i API-et og i auditloggen, mens en egen variant
     * tvinger både oss og konsumentene til å forholde oss til at aktøren faktisk er ukjent.
     */
    data class Infotrygd(
        override val vilkår: Vilkår,
    ) : Opphav
}
