package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.desember
import no.nav.helse.januar
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.rest.GetBehandler
import no.nav.helse.speil.backend.app.rest.KallKontekst
import no.nav.helse.speil.backend.app.rest.RestResponse
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import java.time.Instant

internal class GetVilkårsvurderingerForPersonBehandler : GetBehandler<ApiVilkårsvurderingerForPersonResource, ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil, AppRolle, Transaksjonskontekst> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiVilkårsvurderingerForPersonResource,
        kallKontekst: KallKontekst<Transaksjonskontekst, AppRolle>,
    ): RestResponse<ApiVilkårsvurderingerForPersonResponse, ApiVilkårsvurderingerForPersonFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiVilkårsvurderingerForPersonFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiVilkårsvurderingerForPersonFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val opptjeningsvurderingId = resource.opptjeningsvurderingId

            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp") {
                return@medPerson RestResponse.ok(
                    ApiVilkårsvurderingerForPersonResponse(
                        opptjeningsvurdering =
                            ApiOpptjeningsvurdering(
                                id = opptjeningsvurderingId,
                                rettTilSykepenger = true,
                                skjæringstidspunkt = 1.januar(2018),
                                kodeverkkode = "OPPTJENING_MINST_4_UKER",
                                opphav =
                                    ApiOpphav.Automatisk(
                                        grunnlag =
                                            ApiOpptjeningsgrunnlag.Arbeidstaker(
                                                opptjeningsdager = 31,
                                                opptjeningsperiode = null,
                                                arbeidsforhold =
                                                    listOf(
                                                        ApiArbeidsforhold(
                                                            organisasjonsnummer = "123456789",
                                                            fom = 1.desember(2017),
                                                            tom = 31.desember(2017),
                                                            type = ApiArbeidsforholdtype.ORDINÆRT,
                                                        ),
                                                    ),
                                            ),
                                        versjonAvKildekode = "1",
                                    ),
                                vurdertTidspunkt = Instant.now(),
                            ),
                    ),
                )
            }

            val vurdering = kallKontekst.transaksjon.vilkårsvurderinger.finn(Vilkår.Opptjening, VurderingId(opptjeningsvurderingId))
            if (vurdering == null || vurdering.fødselsnummer != identitetsnummer.value) {
                return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.VurderingIkkeFunnet)
            }
            RestResponse.ok(ApiVilkårsvurderingerForPersonResponse(opptjeningsvurdering = vurdering.tilApiOpptjeningsvurderingResponse()))
        }
    }
}
