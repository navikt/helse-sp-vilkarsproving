package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource

/**
 * POST-endepunktet der en saksbehandler overstyrer en vilkårsvurdering (typisk fordi den automatiske
 * vurderingen kom til feil resultat) for en person, identifisert ved pseudonymisert person-id (jf.
 * kommentaren i [ApiVilkårsvurderingerForPersonResource] om hvorfor det ikke er fødselsnummer).
 *
 * Overstyringen lages som en helt ny [no.nav.helse.sykepenger.vilkarsproving.domain.Kravvurdering] med
 * en ny [no.nav.helse.sykepenger.vilkarsproving.domain.KravvurderingId] — den erstatter den gjeldende
 * vurderingen for kravet/skjæringstidspunktet, den overskriver den ikke.
 */
@Resource("/api/personer/{personId}/vilkarsvurderinger/overstyring")
internal class ApiOverstyrVilkårsvurderingResource(
    val personId: String,
)
