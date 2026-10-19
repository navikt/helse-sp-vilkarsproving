package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Utfall {
    Oppfylt,
    IkkeOppfylt,
}

internal enum class Vilkårskode(
    val lovreferanse: Lovreferanse,
) {
    OPPTJENING_ARBEID_MINST_4_UKER(Lovreferanse.`§ 8-2 første avsnitt, første setning`()),
    OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER(Lovreferanse.`§ 8-2 andre avsnitt, første setning`()),
    OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP(Lovreferanse.`§ 8-2 andre avsnitt, andre setning`()),
    OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER(Lovreferanse.`§ 8-2 andre avsnitt, tredje setning`()),
    ;

    companion object {
        val andreAvsnitt =
            setOf(
                OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER,
                OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP,
                OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER,
            )
    }
}
