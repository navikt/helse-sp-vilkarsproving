package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.ApplicationStarted
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.sykepenger.vilkarsproving.shared.logging.loggInfo
import org.flywaydb.core.Flyway

fun main() {
    val env = System.getenv()
    val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = env.getValue("DATABASE_JDBC_URL")
                maximumPoolSize = 10
            },
        )
    launchApplication(System.getenv(), dataSource)
}

fun launchApplication(
    env: Map<String, String>,
    dataSource: HikariDataSource,
) {
    RapidApplication
        .create(env, builder = {
            withKtorModule {
                monitor.subscribe(ApplicationStarted) {
                    loggInfo("Migrerer database")
                    Flyway
                        .configure()
                        .dataSource(dataSource)
                        .cleanDisabled(true)
                        .lockRetryCount(-1)
                        .load()
                        .migrate()
                    loggInfo("Migrering ferdig!")
                }
            }
        })
        .apply {
            VilkårsprøvingModule(this)
        }.start()
}
