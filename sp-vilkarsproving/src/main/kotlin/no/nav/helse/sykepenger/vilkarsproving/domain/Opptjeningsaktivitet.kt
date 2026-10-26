package no.nav.helse.sykepenger.vilkarsproving.domain

/** Arbeid etter § 8-2 første ledd, og ytelsene som etter andre ledd likestilles med arbeid ved opptjening. */
internal enum class Opptjeningsaktivitet {
    ARBEID,
    DAGPENGER,
    SYKEPENGER,
    KAPITTEL_9,
    FORELDREPENGER,
    SVANGERSKAPSPENGER,
    ;

    val erLikestiltYtelse: Boolean get() = this != ARBEID
}
