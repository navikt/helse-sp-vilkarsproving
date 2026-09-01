package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.Periode

internal sealed interface UtledetFakta {
    data object Ingen : UtledetFakta

    data class Opptjeningstid(
        val opptjeningsperiode: Periode?,
        val opptjeningsdager: Int,
    ) : UtledetFakta
}
