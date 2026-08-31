package no.nav.helse.sykepenger.vilkarsproving.infra.kafka

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import java.time.LocalDate

/**
 * Proaktivt event (ikke et svar på et behov) som forteller utregningsappen for sykepenger at det nå
 * gjelder en ny opptjeningsvurdering for en person på et gitt skjæringstidspunkt — typisk fordi en
 * saksbehandler nettopp har overstyrt en automatisk vurdering, se
 * [no.nav.helse.sykepenger.vilkarsproving.infra.rest.OverstyrVilkårsvurderingBehandler].
 *
 * NB: publiseres av REST-behandleren rett etter at `behandle()` returnerer, altså *før* den
 * omkringliggende databasetransaksjonen faktisk er commitet (REST-rammeverket gir oss ingen "etter
 * commit"-hook). I det (svært sjeldne) tilfellet at selve commit-et skulle feile etter at vi har
 * publisert, vil vi ha fortalt omverdenen om en kravvurdering som ikke ble varig lagret. Dette avviker
 * fra mønsteret ellers i appen (rivere publiserer alltid strengt etter en committet transaksjon) — en
 * bevisst forenkling.
 */
internal object OpptjeningsvurderingOverstyrtMelding {
    private const val EVENT_NAME = "endret_opptjeningsvurdering"

    fun publiser(
        context: MessageContext,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
        opptjeningsvurderingId: KravvurderingId,
    ) {
        val melding =
            JsonMessage.newMessage(
                eventName = EVENT_NAME,
                map =
                    mapOf(
                        "fødselsnummer" to fødselsnummer,
                        "skjæringstidspunkt" to skjæringstidspunkt,
                        "opptjeningsvurderingId" to opptjeningsvurderingId.value,
                        "manuellVurdering" to true,
                    ),
            )
        context.publish(fødselsnummer, melding.toJson())
    }
}
