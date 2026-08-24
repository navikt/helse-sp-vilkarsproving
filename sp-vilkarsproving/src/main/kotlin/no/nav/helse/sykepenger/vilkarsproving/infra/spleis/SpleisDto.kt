package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import java.time.LocalDate

/**
 * Responsen fra `POST /api/opptjeningsvurderinger` i spleis-api.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
internal data class OpptjeningsvurderingerResponse(
    val opptjeningsvurderinger: List<OpptjeningsvurderingDto>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class OpptjeningsvurderingDto(
    val opptjeningsvurderingId: VurderingId,
    val type: OpptjeningsvurderingTypeDto,
    val skjæringstidspunkt: LocalDate,
    val kilde: OpptjeningsvurderingKildeDto,
    // Følgende felter er kun satt når kilden er SPLEIS. Fra INFOTRYGD har vi ingen kjennskap
    // til om, eller hvordan, opptjeningsvilkåret ble vurdert.
    val oppfylt: Boolean? = null,
    val antallDager: Int? = null,
    val opptjeningsperiode: PeriodeDto? = null,
    val arbeidsforhold: List<ArbeidsforholdDto> = emptyList(),
)

internal enum class OpptjeningsvurderingTypeDto {
    ARBEIDSTAKER,
    SELVSTENDIG,
}

internal enum class OpptjeningsvurderingKildeDto {
    INFOTRYGD,
    SPLEIS,
}

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PeriodeDto(
    val fom: LocalDate,
    val tom: LocalDate,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class ArbeidsforholdDto(
    val organisasjonsnummer: String,
    val ansettelsesperioder: List<AnsettelsesperiodeDto>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class AnsettelsesperiodeDto(
    val fom: LocalDate,
    val tom: LocalDate?,
)
