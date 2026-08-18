package no.nav.helse.sykepenger.vilkarsproving.bootstrap

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService
import no.nav.helse.sykepenger.vilkarsproving.infra.db.InMemoryVilkårsprøvingRepository
import no.nav.helse.sykepenger.vilkarsproving.infra.db.InMemoryVilkårsvurderingRepository
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingResultatRiver
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingRiver
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val sikkerLogg: Logger =
    LoggerFactory
        .getLogger("tjenestekall")

class VilkårsprøvingModule(
    rapidsConnection: RapidsConnection,
) {
    private val vilkårsvurderingRepository = InMemoryVilkårsvurderingRepository()
    private val vilkårsprøvingRepository = InMemoryVilkårsprøvingRepository()
    private val opptjeningService = OpptjeningService(vilkårsvurderingRepository, vilkårsprøvingRepository)

    init {
        GrunnlagForAutomatiskArbeidstakerOpptjeningsvurderingRiver(
            rapidsConnection = rapidsConnection,
            opptjeningService = opptjeningService,
        )
        OpptjeningsvurderingRiver(
            rapidsConnection = rapidsConnection,
            opptjeningService = opptjeningService,
        )
        OpptjeningsvurderingResultatRiver(
            rapidsConnection = rapidsConnection,
            vilkårsvurderingRepository = vilkårsvurderingRepository,
        )
    }
}
