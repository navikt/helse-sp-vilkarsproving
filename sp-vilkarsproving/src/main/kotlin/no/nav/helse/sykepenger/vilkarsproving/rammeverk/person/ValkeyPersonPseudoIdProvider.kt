package no.nav.helse.sykepenger.vilkarsproving.rammeverk.person

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoIdClient
import com.github.navikt.tbd_libs.personpseudoid.ValkeyConfig

class ValkeyPersonPseudoIdProvider(
    valkeyConfig: ValkeyConfig,
) : PersonPseudoIdProvider {
    private val client = PersonPseudoIdClient(valkeyConfig)

    override fun nyPersonPseudoId(identitetsnummer: Identitetsnummer): PersonPseudoId = PersonPseudoId(client.nyPersonPseudoId(identitetsnummer.value))

    override fun finnIdentitetsnummer(personPseudoId: PersonPseudoId): Identitetsnummer? = client.finnIdentitetsnummer(personPseudoId.value)?.let { Identitetsnummer(it) }

    companion object {
        fun fraEnv(
            instansNavn: String = "personpseudoid",
            env: Map<String, String> = System.getenv(),
        ): ValkeyPersonPseudoIdProvider {
            val config =
                ValkeyConfig(
                    username = env.getValue("VALKEY_USERNAME_${instansNavn.uppercase()}"),
                    password = env.getValue("VALKEY_PASSWORD_${instansNavn.uppercase()}"),
                    connectionString = env.getValue("VALKEY_URI_${instansNavn.uppercase()}"),
                )
            return ValkeyPersonPseudoIdProvider(config)
        }
    }
}
