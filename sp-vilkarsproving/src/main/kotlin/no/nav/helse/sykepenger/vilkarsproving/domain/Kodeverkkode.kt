package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

/**
 * Hvor smalt en kodeverkkode sier hva som faktisk ble vurdert.
 *
 * En [PRESIS] kode peker på det konkrete rettslige grunnlaget for utfallet. En [GENERELL] kode sier
 * bare at vilkåret er vurdert til utfallet sitt, uten å feste seg til én begrunnelse — det er alt vi
 * kan påstå når grunnlagsdataene mangler, som for vurderinger overført fra Infotrygd
 * (se [Opphav.Infotrygd]).
 */
internal enum class Detaljeringsgrad { PRESIS, GENERELL }

internal enum class Kodeverkkode(
    val vilkår: Vilkår,
    val utfall: Utfall,
    val detaljeringsgrad: Detaljeringsgrad,
) {
    /**
     * Opptjeningen er oppfylt, men vi vet ikke på hvilket grunnlag. Brukes for vurderinger vi har
     * overtatt fra Infotrygd, der vi kun kjenner utfallet.
     */
    OPPTJENING_ARBEID_ELLER_YTELSE(Vilkår.Opptjening, Utfall.Oppfylt, Detaljeringsgrad.GENERELL),

    OPPTJENING_MINST_4_UKER(Vilkår.Opptjening, Utfall.Oppfylt, Detaljeringsgrad.PRESIS),
    OPPTJENING_ANNEN_YTELSE(Vilkår.Opptjening, Utfall.Oppfylt, Detaljeringsgrad.PRESIS),
    OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER(Vilkår.Opptjening, Utfall.Oppfylt, Detaljeringsgrad.PRESIS),

    IKKE_OPPTJENING_AAP_FOER_FORELDREPENGER(Vilkår.Opptjening, Utfall.IkkeOppfylt, Detaljeringsgrad.PRESIS),
    IKKE_OPPTJENING_ARBEID_ELLER_YTELSE(Vilkår.Opptjening, Utfall.IkkeOppfylt, Detaljeringsgrad.GENERELL),
}
