package no.nav.helse.sykepenger.vilkarsproving.domain

import kotlinx.serialization.Serializable

/** `@Serializable` kun brukt av OpenAPI-schema-generatoren (`SchemaGenerator.kotlinx`) — påvirker ikke faktisk (de)serialisering, som fortsatt skjer via Jackson. */
@Serializable
internal enum class Vilkår {
    Opptjening,
    ;

    val regel: Vilkårsregel
        get() =
            when (this) {
                Opptjening -> Opptjeningsregel
            }
}

/** Grunnlag vi må innhente fra andre før et vilkår kan vurderes. */
internal enum class Grunnlagsbehov {
    Arbeidsforhold,
}
