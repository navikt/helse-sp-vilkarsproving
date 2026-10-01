package no.nav.helse.sykepenger.vilkarsproving.rammeverk.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.serialization.customSerializersModule

fun Application.configureResources() {
    install(Resources) {
        serializersModule = customSerializersModule
    }
}
