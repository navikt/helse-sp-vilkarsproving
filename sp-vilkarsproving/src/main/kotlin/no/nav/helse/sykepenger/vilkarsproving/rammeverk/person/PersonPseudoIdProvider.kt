package no.nav.helse.sykepenger.vilkarsproving.rammeverk.person

interface PersonPseudoIdProvider {
    fun nyPersonPseudoId(identitetsnummer: Identitetsnummer): PersonPseudoId

    fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer?
}
