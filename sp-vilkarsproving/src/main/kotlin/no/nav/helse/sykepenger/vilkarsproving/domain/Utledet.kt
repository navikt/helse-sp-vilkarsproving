package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.hendelser.Periode

internal sealed interface Utledet {
    data object IngenUtledning : Utledet

    data class Opptjeningstid(
        val opptjeningsperiode: Periode?,
        val opptjeningsdager: Int,
    ) : Utledet
}
