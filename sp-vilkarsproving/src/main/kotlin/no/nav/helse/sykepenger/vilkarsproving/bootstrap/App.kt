package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.TilgangsgrupperTilBrukerroller
import no.nav.helse.speil.backend.app.bootstrap.AppKonfigurasjon
import no.nav.helse.speil.backend.app.bootstrap.startApp
import no.nav.helse.speil.backend.app.logging.loggInfo
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.OverstyrVilkårsvurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClient

enum class AppRolle(
    override val navn: String,
) : Brukerrolle {
    Saksbehandler("saksbehandler"),
}

private fun erDevGcp(): Boolean = System.getenv()["NAIS_CLUSTER_NAME"] == "dev-gcp"

fun main() {
    val spleisClient = SpleisClient.fromEnv()

    // `endepunkter`-blokken kjøres av `startApp` FØR `rivere`-blokken (routing settes opp før
    // Kafka-tilkoblingen finnes), så `RapidsConnection` kan ikke sendes rett inn i behandleren. Denne
    // referansen fylles ut av `rivere`-blokken under, og leses først når et faktisk HTTP-kall treffer
    // POST-endepunktet — altså lenge etter at begge blokkene er kjørt og appen har startet.
    lateinit var rapidsConnection: RapidsConnection

    startApp(
        konfigurasjon = AppKonfigurasjon.fraEnv("sp-vilkarsproving"),
        brukerroller = TilgangsgrupperTilBrukerroller<AppRolle>(emptyMap()),
        transaksjonProvider = ::PostgresTransaksjonProvider,
        rivere = { dataSource ->
            rapidsConnection = this
            val transaksjonProvider = PostgresTransaksjonProvider(dataSource)
            GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(
                rapidsConnection = this,
                transaksjonProvider = transaksjonProvider,
            )
            OpptjeningsvurderingRiver(
                rapidsConnection = this,
                transaksjonProvider = transaksjonProvider,
            )
            OpptjeningsvurderingResultatRiver(
                rapidsConnection = this,
                transaksjonProvider = transaksjonProvider,
                spleisClient = spleisClient,
            )
        },
        endepunkter = {
            get(GetVilkårsvurderingerForPersonBehandler(SpleisOpptjeningsvurderingService(spleisClient)))
            if (erDevGcp()) {
                loggInfo("setter opp OverstyrVilkårsvurderingBehandler-endepunkt")
                post(OverstyrVilkårsvurderingBehandler(meldingskontekst = { rapidsConnection }))
            }
        },
    )
}
