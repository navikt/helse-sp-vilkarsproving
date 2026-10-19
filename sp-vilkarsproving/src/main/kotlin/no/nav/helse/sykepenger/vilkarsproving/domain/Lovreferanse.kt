package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.januar
import java.time.LocalDate

internal data class Lovreferanse(
    val lov: String,
    val paragraf: String,
    val avsnitt: Int?,
    val setning: Int?,
    val bokstav: String?,
    val iKraftFra: LocalDate,
) {
    companion object {
        fun `§ 8-2 første avsnitt, første setning`(): Lovreferanse =
            Lovreferanse(
                lov = "folketrygdloven",
                paragraf = "8-2",
                avsnitt = 1,
                setning = 1,
                bokstav = null,
                iKraftFra = 1.januar(2004),
            )

        fun `§ 8-2 andre avsnitt, første setning`(): Lovreferanse = `§ 8-2 andre avsnitt`(setning = 1, iKraftFra = 1.januar(1997))

        fun `§ 8-2 andre avsnitt, andre setning`(): Lovreferanse = `§ 8-2 andre avsnitt`(setning = 2, iKraftFra = 1.januar(2018))

        fun `§ 8-2 andre avsnitt, tredje setning`(): Lovreferanse = `§ 8-2 andre avsnitt`(setning = 3, iKraftFra = 1.januar(2018))

        private fun `§ 8-2 andre avsnitt`(
            setning: Int,
            iKraftFra: LocalDate,
        ): Lovreferanse =
            Lovreferanse(
                lov = "folketrygdloven",
                paragraf = "8-2",
                avsnitt = 2,
                setning = setning,
                bokstav = null,
                iKraftFra = iKraftFra,
            )
    }
}
