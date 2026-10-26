package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.februar
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class OpptjeningsvurderingEtterAndreAvsnittTest {
    @Test
    fun `ny vurdering etter andre ledd beholder hovedregelen`() {
        val forrige = opptjeningsvurdering(hovedregelIkkeOppfylt)

        val ny = Opptjeningsvurdering.avSaksbehandlerEtterAndreAvsnitt(FØDSELSNUMMER, 1.februar, listOf(vurdering(L2P1)), forrige)

        assertEquals(setOf(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, L2P1), ny.vilkårsvurderinger.map { it.vilkårskode }.toSet())
    }

    @Test
    fun `ny vurdering etter andre ledd erstatter alle tidligere vurderinger etter andre ledd`() {
        val forrige =
            opptjeningsvurdering(
                hovedregelIkkeOppfylt,
                vurdering(L2P1),
                vurdering(L2P2, Utfall.IkkeOppfylt),
                vurdering(L2P3),
            )
        val nyL2P1 = vurdering(L2P1)

        val ny = Opptjeningsvurdering.avSaksbehandlerEtterAndreAvsnitt(FØDSELSNUMMER, 1.februar, listOf(nyL2P1), forrige)

        assertEquals(listOf(hovedregelIkkeOppfylt.id, nyL2P1.id), ny.vilkårsvurderinger.map { it.id })
    }

    @Test
    fun `ny vurdering etter andre ledd uten forrige vurdering`() {
        val ny = Opptjeningsvurdering.avSaksbehandlerEtterAndreAvsnitt(FØDSELSNUMMER, 1.februar, listOf(vurdering(L2P1)), null)

        assertEquals(listOf(L2P1), ny.vilkårsvurderinger.map { it.vilkårskode })
        assertTrue(ny.erOk)
    }

    @Test
    fun `ny vurdering etter andre ledd på en infotrygdvurdering`() {
        val forrige = Opptjeningsvurdering.fraInfotrygd(fødselsnummer = FØDSELSNUMMER, skjæringstidspunkt = 1.februar, erOk = false, vurdertTidspunkt = Instant.now())

        val ny = Opptjeningsvurdering.avSaksbehandlerEtterAndreAvsnitt(FØDSELSNUMMER, 1.februar, listOf(vurdering(L2P1)), forrige)

        assertEquals(listOf(L2P1), ny.vilkårsvurderinger.map { it.vilkårskode })
    }

    @Test
    fun `samlet vurdering erstatter alle tidligere vilkårsvurderinger`() {
        val forrige = opptjeningsvurdering(hovedregelIkkeOppfylt, vurdering(L2P1), vurdering(L2P2, Utfall.IkkeOppfylt), vurdering(L2P3))
        val nyHovedregel = vurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER)

        val ny = Opptjeningsvurdering.avSaksbehandlerErstattAlle(FØDSELSNUMMER, 1.februar, listOf(nyHovedregel), forrige)

        assertEquals(listOf(nyHovedregel.id), ny.vilkårsvurderinger.map { it.id })
        assertEquals(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, ny.avgjørendeVilkårskode)
        assertTrue(ny.erOk)
    }

    @Test
    fun `samlet vurdering beholder kategorien fra forrige vurdering`() {
        val forrige =
            Opptjeningsvurdering.VurdertISpVilkårsprøving.med(
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                kategori = Kategori.SelvstendigNæringsdrivende,
                vilkårsvurderinger = listOf(hovedregelIkkeOppfylt),
            )

        val ny = Opptjeningsvurdering.avSaksbehandlerErstattAlle(FØDSELSNUMMER, 1.februar, listOf(vurdering(L2P1)), forrige)

        assertEquals(Kategori.SelvstendigNæringsdrivende, ny.kategori)
    }

    private val L2P1 = Vilkårskode.OPPTJENING_LIKESTILT_YTELSE_MINST_4_UKER
    private val L2P2 = Vilkårskode.OPPTJENING_FORELDREPENGER_IKKE_OPPTJENT_PA_AAP
    private val L2P3 = Vilkårskode.OPPTJENING_YRKESAKTIV_MINST_4_UKER_FOR_UTTAK_AV_FORELDREPENGER

    private val hovedregelIkkeOppfylt = vurdering(Vilkårskode.OPPTJENING_ARBEID_MINST_4_UKER, Utfall.IkkeOppfylt)

    private fun vurdering(
        vilkårskode: Vilkårskode,
        utfall: Utfall = Utfall.Oppfylt,
    ) = Vilkårsvurdering.avSaksbehandler(vilkårskode, utfall, "Z999999", "begrunnelse")

    private fun opptjeningsvurdering(vararg vilkårsvurderinger: Vilkårsvurdering) =
        Opptjeningsvurdering.VurdertISpVilkårsprøving.med(
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
            kategori = Kategori.Arbeidstaker,
            vilkårsvurderinger = vilkårsvurderinger.toList(),
        )

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
    }
}
