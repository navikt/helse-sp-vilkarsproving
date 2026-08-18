package no.nav.helse.sykepenger.vilkarsproving.domain

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
