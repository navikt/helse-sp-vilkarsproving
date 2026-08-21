package no.nav.helse.sykepenger.vilkarsproving.rest

import no.nav.helse.speil.backend.app.auth.Tilgang
import no.nav.helse.speil.backend.app.person.PersonPseudoId
import no.nav.helse.speil.backend.app.rest.GetBehandler
import no.nav.helse.speil.backend.app.rest.KallKontekst
import no.nav.helse.speil.backend.app.rest.RestResponse
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import no.nav.helse.sykepenger.vilkarsproving.bootstrap.AppRolle
import no.nav.helse.sykepenger.vilkarsproving.domain.Kilde
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsregel
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId

/**
 * Henter vilkårsvurderinger for en person. Tre modi, styrt av query-parametrene på
 * [VilkårsvurderingerForPersonResource] (minst ett av dem er ALDRI et krav — ingen parametre er en
 * gyldig, egen modus: "hent alt"):
 * - Ingen parametre: alt som er vurdert for personen, på tvers av vilkår og skjæringstidspunkt.
 * - `opptjeningsvurderingId`: den ene, konkrete opptjeningsvurderingen (404 dersom den ikke finnes,
 *   eller finnes men tilhører en annen person enn `personId` — vurderings-ID-er er globalt unike,
 *   så dette må sjekkes eksplisitt for å ikke lekke andre personers vurderinger).
 * - `medlemskapsvurderingId`: IKKE implementert ennå (medlemskapsvilkåret finnes ikke i domenet) —
 *   kaster en feil som fanges av den applikasjonsvide `StatusPages`-pluginen (se
 *   `configureStatusPages`), altså 500 uten detaljer til klienten, tilsvarende alle andre uventede
 *   feil i appen.
 *
 * Krever kun [Tilgang.Les] — dette er et rent oppslag, ingen skriving.
 */
internal class GetVilkårsvurderingerForPersonBehandler : GetBehandler<VilkårsvurderingerForPersonResource, ApiVilkårsvurderingerForPersonResponse, VilkårsvurderingerForPersonFeil, AppRolle, Transaksjonskontekst> {
    override val påkrevdTilgang = Tilgang.Les
    override val tag = "vilkarsvurderinger"

    override fun behandle(
        resource: VilkårsvurderingerForPersonResource,
        kallKontekst: KallKontekst<Transaksjonskontekst, AppRolle>,
    ): RestResponse<ApiVilkårsvurderingerForPersonResponse, VilkårsvurderingerForPersonFeil> {
        val personPseudoId =
            PersonPseudoId.fraString(resource.personId)
                ?: return RestResponse.feil(VilkårsvurderingerForPersonFeil.PersonIkkeFunnet)

        return kallKontekst.medPerson(
            personPseudoId = personPseudoId,
            personIkkeFunnet = { VilkårsvurderingerForPersonFeil.PersonIkkeFunnet },
            manglerTilgang = { VilkårsvurderingerForPersonFeil.ManglerTilgang },
        ) { identitetsnummer ->

            val opptjeningsvurderingId =
                resource.opptjeningsvurderingId ?: return@medPerson RestResponse.Feil(
                    VilkårsvurderingerForPersonFeil.ManglerRequestParameter,
                )

            val vurdering = kallKontekst.transaksjon.vilkårsvurderinger.finn(Vilkår.Opptjening, VurderingId(opptjeningsvurderingId))
            if (vurdering == null || vurdering.fødselsnummer != identitetsnummer.value) {
                // TODO kalle gjennom til spleis i migreringsfasen
                return@medPerson RestResponse.feil(VilkårsvurderingerForPersonFeil.VurderingIkkeFunnet)
            }

            return@medPerson RestResponse.ok(ApiVilkårsvurderingerForPersonResponse(vurdering.tilApiOpptjeningResponse()))
        }
    }
}

/**
 * Kildetypen ([Kilde.Automatisk] vs. [Kilde.Manuell]) avgjør hvilken [ApiOpptjening]-variant vi
 * mapper til — feltene der er ikke overlappende, jf. de to variantene i API-kontrakten.
 *
 * [ApiOpptjeningsperiode] er ikke noe [Opptjeningsregel] beregner og lagrer i dag (den avgjør kun
 * [no.nav.helse.sykepenger.vilkarsproving.domain.Kodeverkkode]), så feltet er alltid `null` her
 * inntil regelen eventuelt utvides til å gi fra seg selve perioden den la til grunn.
 */
private fun Vilkårsvurdering.tilApiOpptjeningResponse(): ApiOpptjening =
    when (val kilde = kilde) {
        is Kilde.Automatisk ->
            ApiOpptjening.Automatisk(
                regelversjon = kilde.regelversjon,
                opptjeningsperiode = null,
                antallDagerPåkrevd = Opptjeningsregel.ANTALL_OPPTJENINGSDAGER_SOM_KREVES,
                id = id.value,
                utfall = utfall.tilApiUtfall(),
                kodeverkkode = kodeverkkode.name,
            )

        is Kilde.Manuell ->
            ApiOpptjening.Manuell(
                saksbehandlerIdent = kilde.saksbehandlerIdent,
                fritekstbegrunnelse = kilde.fritekstbegrunnelse,
                id = id.value,
                utfall = utfall.tilApiUtfall(),
                kodeverkkode = kodeverkkode.name,
            )
    }
