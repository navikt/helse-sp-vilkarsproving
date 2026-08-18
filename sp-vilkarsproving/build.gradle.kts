plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppKt"
}

dependencies {
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.rapids.and.rivers)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.logback)
    implementation(libs.kotliquery)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.tbd.libs.azure)
    implementation(libs.tbd.libs.retry)
    implementation(project(":migreringer"))

    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation(libs.httpclient5.fluent)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.wiremock)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers.postgres)
}
