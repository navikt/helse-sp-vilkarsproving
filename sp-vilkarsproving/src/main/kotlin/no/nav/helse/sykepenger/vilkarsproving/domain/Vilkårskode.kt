package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

internal enum class Vilkårskode {
    OPPTJENING_ARBEID_MINST_4_UKER,

    OPPTJENING_LIKESTILT_YTELSE,

    OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP,

    OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER,
}
