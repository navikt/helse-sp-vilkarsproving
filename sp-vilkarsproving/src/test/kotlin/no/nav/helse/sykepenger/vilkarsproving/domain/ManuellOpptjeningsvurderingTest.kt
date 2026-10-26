package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.ARBEID
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.DAGPENGER
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.FORELDREPENGER
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.SYKEPENGER
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall.IkkeOppfylt
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall.Oppfylt
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering.Companion.opptjeningErOppfylt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

internal class ManuellOpptjeningsvurderingTest {
    @Test
    fun `bare arbeid gir første ledd oppfylt og ingenting etter andre ledd (scenario A)`() {
        val vurderinger = vurder(svar(minst4Uker = true, setOf(ARBEID)))
        assertEquals(listOf(L1 to Oppfylt), vurderinger.koderOgUtfall())
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `likestilt ytelse gir første ledd ikke oppfylt og første punktum oppfylt (scenario B)`() {
        val vurderinger = vurder(svar(minst4Uker = true, setOf(SYKEPENGER)))
        assertEquals(listOf(L1 to IkkeOppfylt, L2P1 to Oppfylt), vurderinger.koderOgUtfall())
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `arbeid kombinert med likestilt ytelse vurderes etter andre ledd`() {
        val vurderinger = vurder(svar(minst4Uker = true, setOf(ARBEID, DAGPENGER)))
        assertEquals(listOf(L1 to IkkeOppfylt, L2P1 to Oppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `foreldrepenger opptjent ved arbeid gir første og andre punktum oppfylt`() {
        val vurderinger = vurder(svar(minst4Uker = true, setOf(FORELDREPENGER), fpAap = false))
        assertEquals(listOf(L1 to IkkeOppfylt, L2P1 to Oppfylt, L2P2 to Oppfylt), vurderinger.koderOgUtfall())
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `foreldrepenger opptjent på AAP med yrkesaktivitet før uttak gir tredje punktum oppfylt (scenario C)`() {
        val vurderinger = vurder(svar(minst4Uker = true, setOf(ARBEID, FORELDREPENGER), fpAap = true, yrkesaktiv = true))
        assertEquals(
            listOf(L1 to IkkeOppfylt, L2P1 to Oppfylt, L2P2 to IkkeOppfylt, L2P3 to Oppfylt),
            vurderinger.koderOgUtfall(),
        )
        assertTrue(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `foreldrepenger opptjent på AAP uten yrkesaktivitet før uttak gir avslag (scenario E)`() {
        val vurderinger = vurder(svar(minst4Uker = true, setOf(FORELDREPENGER), fpAap = true, yrkesaktiv = false))
        assertEquals(
            listOf(L1 to IkkeOppfylt, L2P1 to Oppfylt, L2P2 to IkkeOppfylt, L2P3 to IkkeOppfylt),
            vurderinger.koderOgUtfall(),
        )
        assertFalse(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `ikke minst 4 uker gir første ledd og første punktum ikke oppfylt (scenario D)`() {
        val vurderinger = vurder(svar(minst4Uker = false, emptySet()))
        assertEquals(listOf(L1 to IkkeOppfylt, L2P1 to IkkeOppfylt), vurderinger.koderOgUtfall())
        assertFalse(vurderinger.opptjeningErOppfylt())
    }

    @Test
    fun `alle vurderingene deler tidspunkt og saksbehandlerkilde med opptjeningsaktivitetene`() {
        val tidspunkt = Instant.parse("2025-04-09T10:00:00Z")
        val vurderinger = vurder(svar(minst4Uker = true, setOf(FORELDREPENGER, ARBEID), fpAap = false), tidspunkt)

        val forventetKilde =
            Vurderingskilde.Saksbehandler(
                ident = "Z999999",
                fritekstbegrunnelse = "begrunnelse",
                journalpostId = listOf("journalpost-1"),
                opptjeningsaktiviteter = listOf(ARBEID, FORELDREPENGER),
            )
        vurderinger.forEach {
            assertEquals(tidspunkt, it.vurdertTidspunkt)
            assertEquals(forventetKilde, it.kilde)
            assertEquals(it.vilkårskode.lovreferanse, it.lovreferanse)
        }
    }

    @Test
    fun `minst 4 uker uten opptjeningsaktiviteter er ugyldig`() {
        assertUgyldig(UgyldigSvarPåOpptjening.OPPTJENINGSAKTIVITETER_MANGLER, svar(minst4Uker = true, emptySet()))
    }

    @Test
    fun `opptjeningsaktiviteter uten minst 4 uker er ugyldig`() {
        assertUgyldig(UgyldigSvarPåOpptjening.OPPTJENINGSAKTIVITETER_UTEN_MINST_4_UKER, svar(minst4Uker = false, setOf(ARBEID)))
    }

    @Test
    fun `foreldrepenger uten svar om AAP er ugyldig`() {
        assertUgyldig(UgyldigSvarPåOpptjening.FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER, svar(minst4Uker = true, setOf(FORELDREPENGER)))
    }

    @Test
    fun `svar om AAP uten foreldrepenger er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåOpptjening.FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER,
            svar(minst4Uker = true, setOf(ARBEID), fpAap = false),
        )
    }

    @Test
    fun `foreldrepenger opptjent på AAP uten svar om yrkesaktivitet er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåOpptjening.YRKESAKTIV_FØR_UTTAK_MANGLER,
            svar(minst4Uker = true, setOf(FORELDREPENGER), fpAap = true),
        )
    }

    @Test
    fun `svar om yrkesaktivitet når foreldrepengene ikke er opptjent på AAP er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåOpptjening.YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP,
            svar(minst4Uker = true, setOf(FORELDREPENGER), fpAap = false, yrkesaktiv = true),
        )
    }

    private val L1 = Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER
    private val L2P1 = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER
    private val L2P2 = Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP
    private val L2P3 = Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER

    private fun svar(
        minst4Uker: Boolean,
        opptjeningsaktiviteter: Set<Opptjeningsaktivitet>,
        fpAap: Boolean? = null,
        yrkesaktiv: Boolean? = null,
    ) = SvarPåOpptjening(minst4Uker, opptjeningsaktiviteter, fpAap, yrkesaktiv)

    private fun vurder(
        svar: SvarPåOpptjening,
        tidspunkt: Instant = Instant.now(),
    ): List<Vilkårsvurdering> {
        val resultat = ManuellOpptjeningsvurdering.vurder(svar, "Z999999", "begrunnelse", listOf("journalpost-1"), tidspunkt)
        return (resultat as ManuellOpptjeningsvurderingResultat.Vurdert).vilkårsvurderinger
    }

    private fun assertUgyldig(
        forventet: UgyldigSvarPåOpptjening,
        svar: SvarPåOpptjening,
    ) {
        assertEquals(
            ManuellOpptjeningsvurderingResultat.Ugyldig(forventet),
            ManuellOpptjeningsvurdering.vurder(svar, "Z999999", "begrunnelse", emptyList()),
        )
    }

    private fun List<Vilkårsvurdering>.koderOgUtfall() = map { it.vilkårskode to it.utfall }
}
