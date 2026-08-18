package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.sykepenger.vilkarsproving.infra.db.PostgresTransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.sql.DataSource

internal val sikkerLogg: Logger =
    LoggerFactory
        .getLogger("tjenestekall")

class VilkårsprøvingModule(
    rapidsConnection: RapidsConnection,
    dataSource: DataSource,
) {
    private val transaksjonProvider = PostgresTransaksjonProvider(dataSource)

    init {
        GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(
            rapidsConnection = rapidsConnection,
            transaksjonProvider = transaksjonProvider,
        )
        OpptjeningsvurderingRiver(
            rapidsConnection = rapidsConnection,
            transaksjonProvider = transaksjonProvider,
        )
        OpptjeningsvurderingResultatRiver(
            rapidsConnection = rapidsConnection,
            transaksjonProvider = transaksjonProvider,
        )
    }
}
