package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.PostBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse

// Utgående: erstattet av PostManuellArbeidMinstFireUkerVurderingBehandler. Fjernes når speil har tatt i bruk det nye endepunktet.
internal class PostManuellVilkårsvurderingBehandler :
    PostBehandler<
        ApiManuellVilkårsvurderingResource,
        ApiManuellVilkårsvurderingRequest,
        ApiManuellOpptjeningsvurderingResponse,
        ApiManuellVilkårsvurderingFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiManuellVilkårsvurderingResource,
        request: ApiManuellVilkårsvurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiManuellOpptjeningsvurderingResponse, ApiManuellVilkårsvurderingFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiManuellVilkårsvurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiManuellVilkårsvurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiManuellVilkårsvurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
            if (request.vilkårskode != ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER) {
                return@medPerson RestResponse.feil(ApiManuellVilkårsvurderingFeil.VilkårskodeStøttesIkke)
            }

            val opptjeningsvurderingId =
                lagreManuellVurderingAvArbeidMinstFireUker(
                    kallKontekst = kallKontekst,
                    identitetsnummer = identitetsnummer,
                    skjæringstidspunkt = request.skjæringstidspunkt,
                    utfall = request.utfall.fraApi(),
                    fritekstbegrunnelse = request.fritekstbegrunnelse,
                    journalpostId = request.journalpostId,
                )
            RestResponse.ok(ApiManuellOpptjeningsvurderingResponse(opptjeningsvurderingId = opptjeningsvurderingId))
        }
    }
}
