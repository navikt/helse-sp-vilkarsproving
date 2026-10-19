package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import io.ktor.resources.Resource

@Resource("/api/personer/{personId}/vilkarsvurderinger/manuell/likestilte-ytelser")
internal class ApiManuellLikestiltYtelseVurderingResource(
    val personId: String,
)
