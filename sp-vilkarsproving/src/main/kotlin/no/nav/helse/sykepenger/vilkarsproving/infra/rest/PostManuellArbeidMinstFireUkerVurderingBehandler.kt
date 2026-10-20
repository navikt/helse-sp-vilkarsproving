package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.Identitetsnummer
import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.HandlingType
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxKonvolutt
import no.nav.helse.sykepenger.vilkarsproving.application.OutboxMelding
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.PostBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class PostManuellArbeidMinstFireUkerVurderingBehandler :
    PostBehandler<
        ApiManuellArbeidMinstFireUkerVurderingResource,
        ApiManuellArbeidMinstFireUkerVurderingRequest,
        ApiManuellOpptjeningsvurderingResponse,
        ApiManuellArbeidMinstFireUkerVurderingFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Skriv
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: ApiManuellArbeidMinstFireUkerVurderingResource,
        request: ApiManuellArbeidMinstFireUkerVurderingRequest,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiManuellOpptjeningsvurderingResponse, ApiManuellArbeidMinstFireUkerVurderingFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiManuellArbeidMinstFireUkerVurderingFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiManuellArbeidMinstFireUkerVurderingFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiManuellArbeidMinstFireUkerVurderingFeil.ManglerTilgang },
        ) { identitetsnummer ->
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

internal fun lagreManuellVurderingAvArbeidMinstFireUker(
    kallKontekst: KallKontekst<Transaksjonskontekst>,
    identitetsnummer: Identitetsnummer,
    skjæringstidspunkt: LocalDate,
    utfall: Utfall,
    fritekstbegrunnelse: String,
    journalpostId: List<String>,
): UUID {
    val saksbehandlerIdent = kallKontekst.saksbehandler.navIdent.value
    val vilkårsvurdering =
        Vilkårsvurdering.avSaksbehandler(
            vilkårskode = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
            utfall = utfall,
            saksbehandlerIdent = saksbehandlerIdent,
            fritekstbegrunnelse = fritekstbegrunnelse,
            journalpostId = journalpostId,
        )

    val kravvurdering =
        Opptjeningsvurdering.avSaksbehandler(
            fødselsnummer = identitetsnummer.value,
            skjæringstidspunkt = skjæringstidspunkt,
            vilkårsvurdering = vilkårsvurdering,
            forrigeVurdering =
                kallKontekst.transaksjon.opptjeningsvurderinger.gjeldende(
                    fødselsnummer = identitetsnummer.value,
                    skjæringstidspunkt = skjæringstidspunkt,
                ),
        )

    kallKontekst.transaksjon.opptjeningsvurderinger.lagre(kravvurdering)

    kallKontekst.transaksjon.outbox.leggTil(
        OutboxKonvolutt.ny(
            melding =
                OutboxMelding.Saksbehandlerhandling(
                    skjæringstidspunkt = skjæringstidspunkt,
                    saksbehandlerIdent = saksbehandlerIdent,
                    handlingId = kravvurdering.id.value,
                    handlingType = HandlingType.MANUELT_VURDERT_OPPTJENING,
                    kreverTotrinnsvurdering = true,
                    tidspunkt = Instant.now(),
                ),
            identitetsnummer = identitetsnummer,
        ),
    )
    kallKontekst.transaksjon.outbox.leggTil(
        OutboxKonvolutt.ny(
            melding =
                OutboxMelding.OpptjeningsvurderingEndret(
                    skjæringstidspunkt = skjæringstidspunkt,
                    opptjeningsvurderingId = kravvurdering.id.value,
                    manuellVurdering = true,
                ),
            identitetsnummer = identitetsnummer,
        ),
    )

    return kravvurdering.id.value
}

internal fun ApiUtfall.fraApi(): Utfall =
    when (this) {
        ApiUtfall.OPPFYLT -> Utfall.Oppfylt
        ApiUtfall.IKKE_OPPFYLT -> Utfall.IkkeOppfylt
    }
