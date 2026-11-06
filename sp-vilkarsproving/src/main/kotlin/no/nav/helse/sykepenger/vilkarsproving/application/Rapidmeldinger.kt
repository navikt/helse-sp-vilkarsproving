package no.nav.helse.sykepenger.vilkarsproving.application

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun saksbehandlerhandling(
    fødselsnummer: String,
    skjæringstidspunkt: LocalDate,
    saksbehandlerIdent: String,
    handlingId: UUID,
    handlingType: String,
    kreverTotrinnsvurdering: Boolean,
    tidspunkt: Instant,
): String =
    JsonMessage
        .newMessage(
            "saksbehandlerhandling",
            mapOf(
                "fødselsnummer" to fødselsnummer,
                "skjæringstidspunkt" to skjæringstidspunkt,
                "saksbehandlerIdent" to saksbehandlerIdent,
                "kreverTotrinnsvurdering" to kreverTotrinnsvurdering,
                "handlingId" to handlingId,
                "handlingType" to handlingType,
                "tidspunkt" to tidspunkt,
            ),
        ).toJson()

internal fun endretOpptjeningsvurdering(
    fødselsnummer: String,
    skjæringstidspunkt: LocalDate,
    opptjeningsvurderingId: UUID,
    manuellVurdering: Boolean,
): String =
    JsonMessage
        .newMessage(
            "endret_opptjeningsvurdering",
            mapOf(
                "fødselsnummer" to fødselsnummer,
                "skjæringstidspunkt" to skjæringstidspunkt,
                "opptjeningsvurderingId" to opptjeningsvurderingId,
                "manuellVurdering" to manuellVurdering,
            ),
        ).toJson()
