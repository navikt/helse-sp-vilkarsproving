plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    testImplementation(libs.flyway.database.postgresql) {
        version {
            // Appen får Flyway transitivt fra speil-backend-app. Test også den versjonen før deploy.
            providers.gradleProperty("flywayTestVersion").orNull?.let { strictly(it) }
        }
    }
    testImplementation(libs.testcontainers.postgres)
    testRuntimeOnly(libs.postgresql)
}
