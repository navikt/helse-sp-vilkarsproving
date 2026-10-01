package no.nav.helse.sykepenger.vilkarsproving.rammeverk.testfixtures

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.Identitetsnummer
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.person.PersonPseudoIdProvider
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory [PersonPseudoIdProvider] til bruk i tester — ingen ekte Valkey-avhengighet. */
class InMemoryPersonPseudoIdProvider : PersonPseudoIdProvider {
    private val lagring = ConcurrentHashMap<PersonPseudoId, Identitetsnummer>()

    override fun nyPersonPseudoId(identitetsnummer: Identitetsnummer): PersonPseudoId {
        val id = PersonPseudoId(UUID.randomUUID())
        lagring[id] = identitetsnummer
        return id
    }

    override fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer? = lagring[personPseudoId]

    /** Fjerner en pseudo-id, for å simulere utløpt TTL i tester. */
    fun fjern(personPseudoId: PersonPseudoId) {
        lagring.remove(personPseudoId)
    }
}
