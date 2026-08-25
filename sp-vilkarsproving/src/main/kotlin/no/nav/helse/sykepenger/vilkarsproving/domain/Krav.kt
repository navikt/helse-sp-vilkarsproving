package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Krav {
    Opptjening,
    ;

    val regel: Kravregel
        get() =
            when (this) {
                Opptjening -> Opptjeningsregel
            }
}

internal enum class Grunnlagsbehov {
    Arbeidsforhold,
}
