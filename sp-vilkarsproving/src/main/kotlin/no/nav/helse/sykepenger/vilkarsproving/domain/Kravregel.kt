package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.LocalDate

internal interface Kravregel {
    val krav: Krav
    val versjon: String

    fun vurder(
        skjæringstidspunkt: LocalDate,
        grunnlag: Vilkårsgrunnlag,
    ): Kravregelresultat
}

internal data class Kravregelresultat(
    val sti: List<Vilkårsutfall>,
) {
    init {
        require(sti.isNotEmpty()) { "En regel må ha prøvd minst ett vilkår" }
    }

    val utfall: Utfall get() = sti.last().utfall
}

internal data class Vilkårsutfall(
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val utledet: Utledet,
)
