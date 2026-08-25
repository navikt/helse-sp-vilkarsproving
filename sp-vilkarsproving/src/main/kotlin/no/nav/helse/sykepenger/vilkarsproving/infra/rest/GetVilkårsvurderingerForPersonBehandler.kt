package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import no.nav.helse.desember
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.rest.GetBehandler
import no.nav.helse.speil.backend.app.rest.KallKontekst
import no.nav.helse.speil.backend.app.rest.RestResponse
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Krav
import no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
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
            if (Demodata.erPåskrudd) {
                return@medPerson RestResponse.ok(Demodata.respons())
            }

            val vurdering =
                kallKontekst.transaksjon.kravvurderinger
                    .finn(Krav.Opptjening, KravvurderingId(resource.opptjeningsvurderingId))

            if (vurdering == null || vurdering.fødselsnummer != identitetsnummer.value) {
                return@medPerson RestResponse.feil(ApiVilkårsvurderingerForPersonFeil.VurderingIkkeFunnet)
            }

            RestResponse.ok(Vurderingsrespons.fra(vurdering))
        }
    }
}

private object Demodata {
    val erPåskrudd: Boolean get() = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"

    fun respons(): ApiVilkårsvurderingerForPersonResponse =
        Vurderingsrespons.fra(
            Kravvurdering.automatisk(
                prøvingId = PrøvingId.ny(),
                fødselsnummer = "00000000000",
                skjæringstidspunkt = 1.januar(2018),
                grunnlag =
                    Opptjeningsgrunnlag.Arbeidstaker(
                        listOf(
                            Arbeidsforhold(
                                orgnummer = "123456789",
                                ansettelseperiode = 1.desember(2017) til 31.desember(2017),
                                type = Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT,
                            ),
                        ),
                    ),
                vurdertTidspunkt = Instant.now(),
            ),
        )
}
