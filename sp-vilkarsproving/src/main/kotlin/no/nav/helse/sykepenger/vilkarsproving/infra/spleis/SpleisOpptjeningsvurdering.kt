package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import no.nav.helse.Periode
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.til
import java.time.LocalDate

internal sealed interface SpleisOpptjeningsvurdering {
    val opptjeningsvurderingId: OpptjeningsvurderingId
    val skjæringstidspunkt: LocalDate

    data class SpleisArbeidstaker(
        override val opptjeningsvurderingId: OpptjeningsvurderingId,
        override val skjæringstidspunkt: LocalDate,
        val oppfylt: Boolean,
        val antallDager: Int,
        val opptjeningsperiode: Periode?,
        val arbeidsforhold: List<Arbeidsforhold>,
    ) : SpleisOpptjeningsvurdering {
        data class Arbeidsforhold(
            val organisasjonsnummer: String,
            val ansettelsesperioder: List<Ansettelsesperiode>,
        )

        data class Ansettelsesperiode(
            val fom: LocalDate,
            val tom: LocalDate?,
        ) {
            init {
                tom?.let { fom til tom }
            }
        }
    }

    data class SpleisSelvstendig(
        override val opptjeningsvurderingId: OpptjeningsvurderingId,
        override val skjæringstidspunkt: LocalDate,
    ) : SpleisOpptjeningsvurdering

    data class InfotrygdArbeidstaker(
        override val opptjeningsvurderingId: OpptjeningsvurderingId,
        override val skjæringstidspunkt: LocalDate,
    ) : SpleisOpptjeningsvurdering
}
