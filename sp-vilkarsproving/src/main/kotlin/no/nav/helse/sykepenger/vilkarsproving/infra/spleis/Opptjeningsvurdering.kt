package no.nav.helse.sykepenger.vilkarsproving.infra.spleis

import no.nav.helse.hendelser.Periode
import no.nav.helse.hendelser.til
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import java.time.LocalDate

internal sealed interface Opptjeningsvurdering {
    val opptjeningsvurderingId: VurderingId
    val skjæringstidspunkt: LocalDate

    data class SpleisArbeidstaker(
        override val opptjeningsvurderingId: VurderingId,
        override val skjæringstidspunkt: LocalDate,
        val oppfylt: Boolean,
        val antallDager: Int,
        val opptjeningsperiode: Periode?,
        val arbeidsforhold: List<Arbeidsforhold>,
    ) : Opptjeningsvurdering {
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
        override val opptjeningsvurderingId: VurderingId,
        override val skjæringstidspunkt: LocalDate,
    ) : Opptjeningsvurdering

    data class InfotrygdArbeidstaker(
        override val opptjeningsvurderingId: VurderingId,
        override val skjæringstidspunkt: LocalDate,
    ) : Opptjeningsvurdering
}