package no.nav.helse.sykepenger.vilkarsproving.rammeverk.person

@JvmInline
value class Identitetsnummer(
    val value: String,
) {
    init {
        require(value.matches(Regex("\\d{11}"))) { "Identitetsnummer må bestå av nøyaktig 11 siffer" }
    }

    override fun toString() = "Identitetsnummer(***)" // aldri eksponer verdien i logg/toString
}
