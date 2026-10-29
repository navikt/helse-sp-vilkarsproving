package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService
import no.nav.helse.sykepenger.vilkarsproving.application.OpptjeningService.VurderOpptjeningPåNyttResultat
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsprøvingId
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.PostBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.ktor.auth.Tilgang
import no.nav.sykepenger.libs.logging.loggInfo
import java.time.LocalDate

internal class PostNyAutomatiskVilkårsvurderingBehandler(
    private val publiser: (key: String, melding: String) -> Unit,
    private val versjonAvKode: String = "test",
) : PostBehandler<
        ApiNyAutomatiskVilkårsvurderingResource,
        ApiNyAutomatiskVilkårsvurderingRequest,
        ApiNyAutomatiskVilkårsvurderingResponse,
        ApiNyAutomatiskVilkårsvurderingFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiNyAutomatiskVilkårsvurderingResource,
        request: ApiNyAutomatiskVilkårsvurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiNyAutomatiskVilkårsvurderingResponse, ApiNyAutomatiskVilkårsvurderingFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiNyAutomatiskVilkårsvurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiNyAutomatiskVilkårsvurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiNyAutomatiskVilkårsvurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val resultat =
                OpptjeningService(kallKontekst.transaksjon, versjonAvKode).vurderOpptjeningPåNytt(
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value,
                )

            when (resultat) {
                is VurderOpptjeningPåNyttResultat.NyVurderingForetatt ->
                    RestResponse.ok(
                        ApiNyAutomatiskVilkårsvurderingResponse(
                            status = ApiNyAutomatiskVilkårsvurderingStatus.VURDERT,
                            opptjeningsvurderingId = resultat.opptjeningsvurderingId.value,
                        ),
                    )

                is VurderOpptjeningPåNyttResultat.VenterPåGrunnlag -> {
                    beOmArbeidsforhold(
                        identitetsnummer = identitetsnummer,
                        skjæringstidspunkt = request.skjæringstidspunkt,
                        opptjeningsprøvingId = resultat.opptjeningsprøvingId,
                        saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value,
                    )
                    RestResponse.ok(ApiNyAutomatiskVilkårsvurderingResponse(status = ApiNyAutomatiskVilkårsvurderingStatus.VENTER_PÅ_GRUNNLAG))
                }

                VurderOpptjeningPåNyttResultat.IngenVurderingFunnet ->
                    RestResponse.feil(ApiNyAutomatiskVilkårsvurderingFeil.OpptjeningsvurderingIkkeFunnet)

                VurderOpptjeningPåNyttResultat.PrøvingPågår ->
                    RestResponse.feil(ApiNyAutomatiskVilkårsvurderingFeil.OpptjeningsprøvingPågår)
            }
        }
    }

    private fun beOmArbeidsforhold(
        identitetsnummer: Identitetsnummer,
        skjæringstidspunkt: LocalDate,
        opptjeningsprøvingId: OpptjeningsprøvingId,
        saksbehandlerIdent: String,
    ) {
        val behov =
            JsonMessage.newNeed(
                behov = listOf("ArbeidsforholdV2"),
                map =
                    mapOf(
                        "fødselsnummer" to identitetsnummer.value,
                        "skjæringstidspunkt" to skjæringstidspunkt.toString(),
                        "opptjeningsprøvingId" to opptjeningsprøvingId.toString(),
                        "saksbehandlerIdent" to saksbehandlerIdent,
                    ),
            )
        loggInfo(
            "Publiserer behov for arbeidsforhold til ny opptjeningsvurdering",
            "opptjeningsprøvingId" to opptjeningsprøvingId.toString(),
            "skjæringstidspunkt" to skjæringstidspunkt.toString(),
        )
        publiser(identitetsnummer.value, behov.toJson())
    }
}
