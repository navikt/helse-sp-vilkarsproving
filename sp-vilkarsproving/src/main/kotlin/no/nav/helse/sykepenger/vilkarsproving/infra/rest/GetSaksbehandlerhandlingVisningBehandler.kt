package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import java.util.UUID

internal class GetSaksbehandlerhandlingVisningBehandler :
    GetBehandler<
        ApiSaksbehandlerhandlingVisningResource,
        ApiSaksbehandlerhandlingVisningResponse,
        ApiSaksbehandlerhandlingVisningFeil,
        Transaksjonskontekst,
    > {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "saksbehandlerhandlinger"

    override fun behandle(
        resource: ApiSaksbehandlerhandlingVisningResource,
        kallKontekst: KallKontekst<Transaksjonskontekst>,
    ): RestResponse<ApiSaksbehandlerhandlingVisningResponse, ApiSaksbehandlerhandlingVisningFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(ApiSaksbehandlerhandlingVisningFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { ApiSaksbehandlerhandlingVisningFeil.PersonIkkeFunnet },
            manglerTilgang = { ApiSaksbehandlerhandlingVisningFeil.ManglerTilgang },
        ) { identitetsnummer ->
            val ikkeFunnet = RestResponse.feil(ApiSaksbehandlerhandlingVisningFeil.VisningIkkeFunnet)

            val opptjeningsvurderingId =
                runCatching { UUID.fromString(resource.handlingId) }.getOrNull()
                    ?: return@medPerson ikkeFunnet

            val vurdering =
                kallKontekst.transaksjon.opptjeningsvurderinger
                    .finn(OpptjeningsvurderingId(opptjeningsvurderingId))
                    ?.takeIf { it.fødselsnummer == identitetsnummer.value }
                    ?: return@medPerson ikkeFunnet

            val manuellVurdering = vurdering.nyesteManuelleVilkårsvurdering() ?: return@medPerson ikkeFunnet

            RestResponse.ok(ApiSaksbehandlerhandlingVisningResponse(felter = manuellVurdering.tilVisningsfelter()))
        }
    }
}

// Tidligere manuelle vurderinger følger med inn i en ny opptjeningsvurdering, så den nyeste er den denne handlingen laget.
private fun Opptjeningsvurdering.nyesteManuelleVilkårsvurdering(): ManuellVilkårsvurdering? =
    (this as? Opptjeningsvurdering.MedVilkårsvurderinger)
        ?.vilkårsvurderinger
        ?.mapNotNull { vurdering -> (vurdering.kilde as? Vurderingskilde.Saksbehandler)?.let { ManuellVilkårsvurdering(vurdering, it) } }
        ?.maxByOrNull { it.vilkårsvurdering.vurdertTidspunkt }

private data class ManuellVilkårsvurdering(
    val vilkårsvurdering: Vilkårsvurdering,
    val kilde: Vurderingskilde.Saksbehandler,
)

private fun ManuellVilkårsvurdering.tilVisningsfelter(): List<ApiVisningsfelt> =
    buildList {
        add(ApiVisningsfelt.Tekst(tittel = "Vilkår", tekst = vilkårsvurdering.vilkårskode.visningsnavn()))
        add(ApiVisningsfelt.Tekst(tittel = "Utfall", tekst = vilkårsvurdering.utfall.visningsnavn()))
        add(ApiVisningsfelt.Tekst(tittel = "Begrunnelse", tekst = kilde.fritekstbegrunnelse))
        if (kilde.journalpostId.isNotEmpty()) {
            add(ApiVisningsfelt.Liste(tittel = "Journalposter", elementer = kilde.journalpostId))
        }
    }

private fun Vilkårskode.visningsnavn(): String =
    when (this) {
        Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER -> "Arbeid i minst 4 uker"
    }

private fun Utfall.visningsnavn(): String =
    when (this) {
        Utfall.Oppfylt -> "Oppfylt"
        Utfall.IkkeOppfylt -> "Ikke oppfylt"
    }
