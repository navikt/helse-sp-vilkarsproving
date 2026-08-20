package no.nav.helse.sykepenger.vilkarsproving.domain

import kotlinx.serialization.Serializable

/** `@Serializable` kun brukt av OpenAPI-schema-generatoren — påvirker ikke Jackson-basert (de)serialisering. */
@Serializable
internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

internal enum class Kodeverkkode(
    val vilkår: Vilkår,
    val utfall: Utfall,
) {
    OPPTJENING_MINST_4_UKER(Vilkår.Opptjening, Utfall.Oppfylt),
    OPPTJENING_ANNEN_YTELSE(Vilkår.Opptjening, Utfall.Oppfylt),
    OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER(Vilkår.Opptjening, Utfall.Oppfylt),

    IKKE_OPPTJENING_AAP_FOER_FORELDREPENGER(Vilkår.Opptjening, Utfall.IkkeOppfylt),
    IKKE_OPPTJENING_ARBEID_ELLER_YTELSE(Vilkår.Opptjening, Utfall.IkkeOppfylt),
}
