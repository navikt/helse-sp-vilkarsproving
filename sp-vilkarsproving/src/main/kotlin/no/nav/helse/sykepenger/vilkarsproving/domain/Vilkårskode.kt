package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

internal enum class Vilkårskode(
    val krav: Krav,
) {
    OPPTJENING_ARBEID_MINST_4_UKER(Krav.Opptjening),

    OPPTJENING_LIKESTILT_YTELSE(Krav.Opptjening),

    OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP(Krav.Opptjening),

    OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER(Krav.Opptjening),
}
