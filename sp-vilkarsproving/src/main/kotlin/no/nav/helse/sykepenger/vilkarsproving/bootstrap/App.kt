package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.TilgangsgrupperTilBrukerroller
import no.nav.helse.speil.backend.app.bootstrap.AppKonfigurasjon
import no.nav.helse.speil.backend.app.bootstrap.startApp
import no.nav.helse.speil.backend.app.rest.RestRuting
import no.nav.helse.sykepenger.vilkarsproving.application.SpleisOpptjeningsvurderingService
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OutboxPubliseringsjobb
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.PostManuellVilkårsvurderingBehandler
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.ISpleisClient
import no.nav.helse.sykepenger.vilkarsproving.infra.spleis.SpleisClient

enum class AppRolle(
    override val navn: String,
) : Brukerrolle {
    Saksbehandler("saksbehandler"),
}

fun main() {
    val spleisClient = SpleisClient.fromEnv()

    startApp(
        konfigurasjon = AppKonfigurasjon.fraEnv("sp-vilkarsproving"),
        brukerroller = TilgangsgrupperTilBrukerroller(emptyMap()),
        transaksjonProvider = ::PostgresTransaksjonProvider,
        rivere = { transaksjonProvider ->
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
            register(
                OutboxPubliseringsjobb(
                    rapidsConnection = this,
                    transaksjonProvider = transaksjonProvider,
                ),
            )
        },
        endepunkter = endepunkter(spleisClient = spleisClient),
    )
}

/**
 * Appens HTTP-endepunkter, definert ett sted. Både produksjonsappen ([main]) og LocalApp bruker
 * denne, slik at et nytt endepunkt bare trenger å registreres her for å bli med begge steder.
 */
internal fun endepunkter(spleisClient: ISpleisClient): RestRuting<AppRolle, Transaksjonskontekst>.() -> Unit =
    {
        get(GetVilkårsvurderingerForPersonBehandler(SpleisOpptjeningsvurderingService(spleisClient)))
        post(PostManuellVilkårsvurderingBehandler())
    }
