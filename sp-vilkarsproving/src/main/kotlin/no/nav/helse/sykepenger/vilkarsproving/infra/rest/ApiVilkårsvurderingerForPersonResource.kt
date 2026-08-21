@file:UseContextualSerialization(UUID::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource
import kotlinx.serialization.UseContextualSerialization
import java.util.UUID

/**
 * GET-endepunktet som henter vilkårsvurderinger for en person, identifisert ved pseudonymisert
 * person-id (aldri fødselsnummer i URL-en, jf. sikkerhetssjekklisten i ).
 *
 * Uten query-parametre: alt som er vurdert for personen (se [GetVilkårsvurderingerForPersonBehandler]).
 * Med `opptjeningsvurderingId`: kun den ene, konkrete opptjeningsvurderingen. Med
 * `medlemskapsvurderingId`: foreløpig ikke implementert (medlemskapsvilkåret finnes ikke i domenet
 * ennå) — behandleren kaster en feil for dette tilfellet.
 *
 * `personId` er (fortsatt) en rå `String`, ikke `UUID`: ktors `Resources`-plugin deserialiserer
 * path-/query-parametre via kotlinx.serialization, og en ugyldig `UUID` der ville feilet FØR
 * behandleren i det hele tatt kjøres (StatusPages ville svart 400, ikke 404). Det er et ,
 * allerede testet kontraktvalg at ugyldig/ukjent person-id skal gi 404 (se
 * `PersonPseudoId.fraString` i behandleren) — å bytte til `UUID` her ville endret den kontrakten.
 * De to nye vurderings-ID-ene under er derimot ekte `UUID`, typet og synlige i OpenAPI-spec-en (jf.
 * og s mønster for kontekstuell UUID-serialisering) — en ugyldig UUID i disse
 * gir 400 Bad Request, som er riktig for et rent syntaktisk ugyldig query-parameter.
 *
 * Resource-klassen implementerer IKKE `PersonResource`: [GetVilkårsvurderingerForPersonBehandler]
 * slår selv opp identitetsnummeret via `KallKontekst.medPerson` (se -skissen for
 * `GetVilkårsvurderingBehandler`), fordi behandleren uansett trenger identitetsnummeret for å spørre
 * repositoriene — å i tillegg implementere `PersonResource` ville gitt et duplikat
 * tilgangskontroll-/auditlogg-kall per forespørsel.
 */
@Resource("/api/personer/{personId}/vilkarsvurderinger")
internal class ApiVilkårsvurderingerForPersonResource(
    val personId: String,
    val opptjeningsvurderingId: UUID,
)
