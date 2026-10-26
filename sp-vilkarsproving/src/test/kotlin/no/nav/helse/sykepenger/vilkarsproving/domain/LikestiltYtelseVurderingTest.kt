package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.DAGPENGER
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.FORELDREPENGER
import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsaktivitet.SYKEPENGER
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall.IkkeOppfylt
import no.nav.helse.sykepenger.vilkarsproving.domain.Utfall.Oppfylt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Scenarioene speiler `SCENARIOER` i docs/opptjening-8-2-andre-ledd/data.js.
 */
internal class LikestiltYtelseVurderingTest {
    @Test
    fun `sykepenger rett før gir bare første punktum, oppfylt`() {
        val vurderinger = vurder(svar(sammenlagt = true, ytelser = listOf(SYKEPENGER)))
        assertEquals(listOf(L2P1 to Oppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `dagpenger rett før gir bare første punktum, oppfylt`() {
        val vurderinger = vurder(svar(sammenlagt = true, ytelser = listOf(DAGPENGER)))
        assertEquals(listOf(L2P1 to Oppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `under 4 uker sammenlagt gir bare første punktum, ikke oppfylt`() {
        val vurderinger = vurder(svar(sammenlagt = false, ytelser = emptyList()))
        assertEquals(listOf(L2P1 to IkkeOppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `foreldrepenger opptjent ved arbeid gir første og andre punktum, begge oppfylt`() {
        val vurderinger = vurder(svar(sammenlagt = true, ytelser = listOf(FORELDREPENGER), fpAap = false))
        assertEquals(listOf(L2P1 to Oppfylt, L2P2 to Oppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `foreldrepenger opptjent på AAP uten yrkesaktivitet før uttak gir alle tre punktum`() {
        val vurderinger = vurder(svar(sammenlagt = true, ytelser = listOf(FORELDREPENGER), fpAap = true, yrkesaktiv = false))
        assertEquals(listOf(L2P1 to Oppfylt, L2P2 to IkkeOppfylt, L2P3 to IkkeOppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `foreldrepenger opptjent på AAP med yrkesaktivitet før uttak gir tredje punktum oppfylt`() {
        val vurderinger = vurder(svar(sammenlagt = true, ytelser = listOf(FORELDREPENGER), fpAap = true, yrkesaktiv = true))
        assertEquals(listOf(L2P1 to Oppfylt, L2P2 to IkkeOppfylt, L2P3 to Oppfylt), vurderinger.koderOgUtfall())
    }

    @Test
    fun `alle vurderingene deler tidspunkt og saksbehandlerkilde med ytelsene`() {
        val tidspunkt = Instant.parse("2025-04-09T10:00:00Z")
        val vurderinger =
            vurder(svar(sammenlagt = true, ytelser = listOf(SYKEPENGER, FORELDREPENGER), fpAap = true, yrkesaktiv = true), tidspunkt)

        val forventetKilde =
            Vurderingskilde.Saksbehandler(
                ident = "Z999999",
                fritekstbegrunnelse = "begrunnelse",
                journalpostId = listOf("journalpost-1"),
                opptjeningsaktiviteter = listOf(SYKEPENGER, FORELDREPENGER),
            )
        vurderinger.forEach {
            assertEquals(tidspunkt, it.vurdertTidspunkt)
            assertEquals(forventetKilde, it.kilde)
            assertEquals(it.vilkårskode.lovreferanse, it.lovreferanse)
        }
    }

    @Test
    fun `sammenlagt minst 4 uker uten ytelser er ugyldig`() {
        assertUgyldig(UgyldigSvarPåLikestiltYtelse.YTELSER_MANGLER, svar(sammenlagt = true, ytelser = emptyList()))
    }

    @Test
    fun `ytelser når sammenlagt er under 4 uker er ugyldig`() {
        assertUgyldig(UgyldigSvarPåLikestiltYtelse.YTELSER_UTEN_SAMMENLAGT_OPPTJENING, svar(sammenlagt = false, ytelser = listOf(SYKEPENGER)))
    }

    @Test
    fun `foreldrepenger uten svar om AAP er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåLikestiltYtelse.FORELDREPENGER_OPPTJENT_PA_AAP_MANGLER,
            svar(sammenlagt = true, ytelser = listOf(FORELDREPENGER)),
        )
    }

    @Test
    fun `svar om AAP uten foreldrepenger er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåLikestiltYtelse.FORELDREPENGER_OPPTJENT_PA_AAP_UTEN_FORELDREPENGER,
            svar(sammenlagt = true, ytelser = listOf(SYKEPENGER), fpAap = false),
        )
    }

    @Test
    fun `foreldrepenger opptjent på AAP uten svar om yrkesaktivitet er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåLikestiltYtelse.YRKESAKTIV_FØR_UTTAK_MANGLER,
            svar(sammenlagt = true, ytelser = listOf(FORELDREPENGER), fpAap = true),
        )
    }

    @Test
    fun `svar om yrkesaktivitet når foreldrepengene ikke er opptjent på AAP er ugyldig`() {
        assertUgyldig(
            UgyldigSvarPåLikestiltYtelse.YRKESAKTIV_FØR_UTTAK_UTEN_FORELDREPENGER_OPPTJENT_PA_AAP,
            svar(sammenlagt = true, ytelser = listOf(FORELDREPENGER), fpAap = false, yrkesaktiv = true),
        )
    }

    private val L2P1 = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER
    private val L2P2 = Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP
    private val L2P3 = Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER

    private fun svar(
        sammenlagt: Boolean,
        ytelser: List<Opptjeningsaktivitet>,
        fpAap: Boolean? = null,
        yrkesaktiv: Boolean? = null,
    ) = SvarPåLikestiltYtelse(sammenlagt, ytelser, fpAap, yrkesaktiv)

    private fun vurder(
        svar: SvarPåLikestiltYtelse,
        tidspunkt: Instant = Instant.now(),
    ): List<Vilkårsvurdering> {
        val resultat = LikestiltYtelseVurdering.vurder(svar, "Z999999", "begrunnelse", listOf("journalpost-1"), tidspunkt)
        return (resultat as LikestiltYtelseVurderingResultat.Vurdert).vilkårsvurderinger
    }

    private fun assertUgyldig(
        forventet: UgyldigSvarPåLikestiltYtelse,
        svar: SvarPåLikestiltYtelse,
    ) {
        assertEquals(
            LikestiltYtelseVurderingResultat.Ugyldig(forventet),
            LikestiltYtelseVurdering.vurder(svar, "Z999999", "begrunnelse", emptyList()),
        )
    }

    private fun List<Vilkårsvurdering>.koderOgUtfall() = map { it.vilkårskode to it.utfall }
}
