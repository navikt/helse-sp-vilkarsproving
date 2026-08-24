package no.nav.helse.sykepenger.vilkarsproving.rest

import no.nav.helse.februar
import no.nav.helse.hendelser.til
import no.nav.helse.januar
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold
import no.nav.helse.sykepenger.vilkarsproving.domain.Arbeidsforhold.Arbeidsforholdtype.ORDINÆRT
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.domain.PrøvingId
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiOpphav
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiOpptjeningsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.tilApiOpptjeningsvurderingResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ApiVilkårsvurderingerForPersonResponseTest {
    @Test
    fun `arbeidstakerresponsen inneholder vurdert opptjeningsperiode og opptjeningsdager`() {
        val vurdering =
            Vilkårsvurdering.automatisk(
                prøvingId = PrøvingId.ny(),
                fødselsnummer = "12345678901",
                skjæringstidspunkt = 1.februar,
                grunnlag =
                    Opptjeningsgrunnlag.Arbeidstaker(
                        listOf(
                            Arbeidsforhold(
                                orgnummer = "987654321",
                                ansettelseperiode = 4.januar til 31.januar,
                                type = ORDINÆRT,
                            ),
                        ),
                    ),
                vurdertTidspunkt = Instant.parse("2024-02-01T12:00:00Z"),
            )

        val opphav = vurdering.tilApiOpptjeningsvurderingResponse().opphav as ApiOpphav.Automatisk
        val grunnlag = opphav.grunnlag as ApiOpptjeningsgrunnlag.Arbeidstaker

        assertEquals(4.januar, grunnlag.opptjeningsperiode?.fom)
        assertEquals(31.januar, grunnlag.opptjeningsperiode?.tom)
        assertEquals(28, grunnlag.opptjeningsdager)
    }
}
