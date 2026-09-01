package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.rest.KallKontekst
import no.nav.helse.speil.backend.app.rest.PostBehandler
import no.nav.helse.speil.backend.app.rest.RestResponse
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.kafka.OpptjeningsvurderingOverstyrtMelding
import java.time.Instant

internal class OverstyrVilkårsvurderingBehandler(
    private val meldingskontekst: () -> MessageContext,
) : PostBehandler<
        ApiOverstyrVilkårsvurderingResource,
        ApiOverstyrVilkårsvurderingRequest,
        ApiOverstyrVilkårsvurderingResponse,
        ApiOverstyrVilkårsvurderingFeil,
        AppRolle,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiOverstyrVilkårsvurderingResource,
        request: ApiOverstyrVilkårsvurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst, AppRolle>,
    ): RestResponse<ApiOverstyrVilkårsvurderingResponse, ApiOverstyrVilkårsvurderingFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiOverstyrVilkårsvurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiOverstyrVilkårsvurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiOverstyrVilkårsvurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val vilkårskode = request.vilkårskode.fraApi()

            val vilkårsvurdering =
                Vilkårsvurdering.avSaksbehandler(
                    vilkårskode = vilkårskode,
                    utfall = request.utfall.fraApi(),
                    saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value,
                    fritekstbegrunnelse = request.fritekstbegrunnelse,
                    vurdertTidspunkt = Instant.now(),
                )

            val kravvurdering =
                Opptjeningsvurdering.avSaksbehandler(
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    sti = listOf(vilkårsvurdering),
                )

            kallKontekst.transaksjon.opptjeningsvurderinger.lagre(kravvurdering)

            OpptjeningsvurderingOverstyrtMelding.publiser(
                context = meldingskontekst(),
                fødselsnummer = identitetsnummer.value,
                skjæringstidspunkt = kravvurdering.skjæringstidspunkt,
                opptjeningsvurderingId = kravvurdering.id,
            )

            RestResponse.ok(ApiOverstyrVilkårsvurderingResponse(opptjeningsvurderingId = kravvurdering.id.value))
        }
    }
}

private fun ApiVilkårskode.fraApi(): Vilkårskode =
    when (this) {
        ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER -> Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
        ApiVilkårskode.OPPTJENING_LIKESTILT_YTELSE -> Vilkårskode.OPPTJENING_LIKESTILT_YTELSE
        ApiVilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP ->
            Vilkårskode.OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP
        ApiVilkårskode.OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER -> Vilkårskode.OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER
    }

private fun ApiUtfall.fraApi(): Utfall =
    when (this) {
        ApiUtfall.OPPFYLT -> Utfall.Oppfylt
        ApiUtfall.IKKE_OPPFYLT -> Utfall.IkkeOppfylt
    }
