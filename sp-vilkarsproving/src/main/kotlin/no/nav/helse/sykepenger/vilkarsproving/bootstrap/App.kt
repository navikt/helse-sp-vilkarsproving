package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import no.nav.helse.speil.backend.app.auth.Brukerrolle
import no.nav.helse.speil.backend.app.auth.TilgangsgrupperTilBrukerroller
import no.nav.helse.speil.backend.app.bootstrap.AppKonfigurasjon
import no.nav.helse.speil.backend.app.bootstrap.startApp
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.GetVilkårsvurderingerForPersonBehandler

enum class AppRolle(
    override val navn: String,
) : Brukerrolle {
    Saksbehandler("saksbehandler"),
}

fun main() {
    startApp(
        konfigurasjon = AppKonfigurasjon.fraEnv("sp-vilkarsproving"),
        brukerroller = TilgangsgrupperTilBrukerroller<AppRolle>(emptyMap()),
        transaksjonProvider = ::PostgresTransaksjonProvider,
        rivere = { dataSource ->
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
            )
        },
        endepunkter = {
            get(GetVilkårsvurderingerForPersonBehandler())
        },
    )
}
