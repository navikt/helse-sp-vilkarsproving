plugins {
    id("no.nav.helse.sas.sas-deployable")
    // Kreves av ktors Resources-plugin for å generere serializers for @Resource-klassene
    alias(libs.plugins.kotlin.serialization)
    `java-test-fixtures`
}

sasDeployable {
    mainClass = "no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppKt"
}

dependencies {
    implementation(libs.tbd.libs.speil.backend.app)

    implementation(libs.kotliquery)
    implementation(project(":migreringer"))
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation(libs.wiremock)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.tbd.libs.speil.backend.app) {
        capabilities {
            requireCapability("tbd-libs:speil-backend-app-test-fixtures")
        }
        // Test fixtures drar inn org.wiremock:wiremock, som er bygget for Jetty 11 og henter
        // versjoner fra jetty-bom 11. Vi tvinger Jetty 12 via plattformen, og da finnes ikke
        // Jetty 11-artefaktene (jetty-servlet, jetty-servlets, jetty-webapp, http2-server) lenger.
        // Vi bruker wiremock-jetty12 i stedet, som allerede ekskluderer Jetty 11-avhengighetene.
        exclude(group = "org.wiremock", module = "wiremock")
    }
}
