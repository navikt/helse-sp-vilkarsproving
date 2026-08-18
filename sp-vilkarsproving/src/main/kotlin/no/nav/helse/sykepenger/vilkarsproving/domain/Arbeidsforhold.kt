package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import java.time.LocalDate

internal data class Arbeidsforhold(
    val orgnummer: String,
    val ansettelseperiode: Periode,
    val type: Arbeidsforholdtype,
) {
    enum class Arbeidsforholdtype {
        FORENKLET_OPPGJØRSORDNING,
        FRILANSER,
        MARITIMT,
        ORDINÆRT,
    }

    constructor(orgnummer: String, ansattFom: LocalDate, ansattTom: LocalDate? = null, type: Arbeidsforholdtype) : this(orgnummer, ansattFom til (ansattTom ?: LocalDate.MAX), type)

    init {
        check(orgnummer.isNotBlank())
    }
}
