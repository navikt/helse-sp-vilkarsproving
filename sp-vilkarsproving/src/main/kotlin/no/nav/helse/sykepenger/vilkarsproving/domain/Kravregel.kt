package no.nav.helse.sykepenger.vilkarsproving.domain

import java.time.LocalDate

internal interface Kravregel {
    val krav: Krav
    val versjon: String

    fun vurder(
        skjæringstidspunkt: LocalDate,
        grunnlag: Opptjeningsgrunnlag,
    ): Kravregelresultat
}

internal data class Kravregelresultat(
    val vilkårsutfall: List<Vilkårsutfall>,
) {
    init {
        require(vilkårsutfall.isNotEmpty()) { "En regel må ha prøvd minst ett vilkår" }
    }

    val utfall: Utfall get() = vilkårsutfall.last().utfall
}

internal data class Vilkårsutfall(
    val vilkårskode: Vilkårskode,
    val utfall: Utfall,
    val utledetFakta: UtledetFakta,
)
