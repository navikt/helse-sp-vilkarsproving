package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

internal enum class Vilkårskode(
    val lovreferanse: Lovreferanse,
) {
    OPPTJENING_ARBEID_MINST_4_UKER(Lovreferanse.`§ 8-2 første avsnitt, første setning`()),
}
