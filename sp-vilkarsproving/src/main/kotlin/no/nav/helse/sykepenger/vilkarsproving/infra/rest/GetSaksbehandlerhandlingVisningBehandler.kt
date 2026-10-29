package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.github.navikt.tbd_libs.personpseudoid.PersonPseudoId
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårskode
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.Vurderingskilde
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.GetBehandler
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.KallKontekst
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest.RestResponse
import no.nav.sykepenger.libs.ktor.auth.Tilgang
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

            val manuellHandling = vurdering.nyesteManuelleHandling() ?: return@medPerson ikkeFunnet

            RestResponse.ok(
                ApiSaksbehandlerhandlingVisningResponse(felter = manuellHandling.tilVisningsfelter(opptjeningOk = vurdering.erOk)),
            )
        }
    }
}

// Tidligere manuelle vurderinger følger med inn i en ny opptjeningsvurdering, så den nyeste er den denne handlingen laget.
// Vurderinger etter § 8-2 andre ledd lages sammen i én handling, med samme tidspunkt og samme kilde.
private fun Opptjeningsvurdering.nyesteManuelleHandling(): ManuellHandling? {
    val manuelle =
        (this as? Opptjeningsvurdering.MedVilkårsvurderinger)
            ?.vilkårsvurderinger
            ?.mapNotNull { vurdering -> (vurdering.kilde as? Vurderingskilde.Saksbehandler)?.let { vurdering to it } }
            ?: return null
    val (nyeste, kilde) = manuelle.maxByOrNull { (vurdering, _) -> vurdering.vurdertTidspunkt } ?: return null
    val vurderinger =
        manuelle
            .filter { (vurdering, k) -> vurdering.vurdertTidspunkt == nyeste.vurdertTidspunkt && k == kilde }
            .map { (vurdering, _) -> vurdering }
    return ManuellHandling(vurderinger, kilde)
}

private data class ManuellHandling(
    val vilkårsvurderinger: List<Vilkårsvurdering>,
    val kilde: Vurderingskilde.Saksbehandler,
)

// Feltene kommer i samme rekkefølge som spørsmålene i skjemaet: aktivitetene før spørsmålene om foreldrepenger
private fun ManuellHandling.tilVisningsfelter(opptjeningOk: Boolean): List<ApiVisningsfelt> =
    buildList {
        add(ApiVisningsfelt.Tekst(tittel = "Opptjeningstid", tekst = if (opptjeningOk) "Oppfylt" else "Ikke oppfylt"))
        val (foreldrepengevilkår, minst4Ukervilkår) =
            vilkårsvurderinger.sortedBy { it.vilkårskode.ordinal }.partition { it.vilkårskode.gjelderForeldrepenger() }
        minst4Ukervilkår.forEach { add(it.tilVisningsfelt()) }
        // Bare arbeid følger av at «Arbeid i minst 4 uker» er oppfylt, så lista vises bare når det er likestilte ytelser
        if (kilde.opptjeningsaktiviteter.any { it != Opptjeningsaktivitet.ARBEID }) {
            add(
                ApiVisningsfelt.Liste(
                    tittel = "Arbeid og likestilte ytelser",
                    elementer = kilde.opptjeningsaktiviteter.map { it.visningsnavn() },
                ),
            )
        }
        foreldrepengevilkår.forEach { add(it.tilVisningsfelt()) }
        add(ApiVisningsfelt.Tekst(tittel = "Begrunnelse", tekst = kilde.fritekstbegrunnelse))
        if (kilde.journalpostId.isNotEmpty()) {
            add(ApiVisningsfelt.Liste(tittel = "Journalposter", elementer = kilde.journalpostId))
        }
    }

private fun Vilkårsvurdering.tilVisningsfelt() = ApiVisningsfelt.Tekst(tittel = vilkårskode.spørsmål(), tekst = svar())

private fun Vilkårskode.gjelderForeldrepenger(): Boolean =
    when (this) {
        Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
        Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER,
        -> false
        Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP,
        Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER,
        -> true
    }

// Vilkårene vises som spørsmålene saksbehandleren svarte på i skjemaet
private fun Vilkårskode.spørsmål(): String =
    when (this) {
        Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER -> "Arbeid i minst 4 uker"
        Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER -> "Likestilt ytelse i minst 4 uker"
        Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP -> "Foreldrepenger opptjent på AAP"
        Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER ->
            "Yrkesaktiv eller likestilt ytelse i minst 4 uker før uttak av foreldrepenger"
    }

// Vilkåret «Foreldrepenger ikke opptjent på AAP» er negert i forhold til spørsmålet, så svaret snus
private fun Vilkårsvurdering.svar(): String {
    val oppfylt = utfall == Utfall.Oppfylt
    val ja = if (vilkårskode == Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP) !oppfylt else oppfylt
    return if (ja) "Ja" else "Nei"
}

private fun Opptjeningsaktivitet.visningsnavn(): String =
    when (this) {
        Opptjeningsaktivitet.ARBEID -> "Arbeid"
        Opptjeningsaktivitet.DAGPENGER -> "Dagpenger"
        Opptjeningsaktivitet.SYKEPENGER -> "Sykepenger"
        Opptjeningsaktivitet.KAPITTEL_9 -> "Pleie-, omsorgs- eller opplæringspenger"
        Opptjeningsaktivitet.FORELDREPENGER -> "Foreldrepenger"
        Opptjeningsaktivitet.SVANGERSKAPSPENGER -> "Svangerskapspenger"
    }
