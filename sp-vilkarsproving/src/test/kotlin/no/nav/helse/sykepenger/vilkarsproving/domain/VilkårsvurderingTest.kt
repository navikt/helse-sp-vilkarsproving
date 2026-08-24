package no.nav.helse.sykepenger.vilkarsproving.domain

import no.nav.helse.februar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

internal class VilkårsvurderingTest {
    // Fra Infotrygd vet vi bare at vilkåret er vurdert, ikke på hvilket grunnlag. Da kan vi heller
    // ikke påstå en presis kode.
    @Test
    fun `infotrygdvurdering kan ikke ha en presis kodeverkkode`() {
        assertThrows<IllegalStateException> { infotrygdvurdering(Kodeverkkode.OPPTJENING_MINST_4_UKER) }
    }

    @Test
    fun `infotrygdvurdering har generell kode, ingen prøving og ukjent saksbehandler`() {
        val vurdering = infotrygdvurdering(Kodeverkkode.OPPTJENING_ARBEID_ELLER_YTELSE)

        assertNull(vurdering.prøvingId)
        assertEquals(Opphav.Infotrygd(Vilkår.Opptjening), vurdering.opphav)
        assertEquals(Vilkår.Opptjening, vurdering.vilkår)
        assertEquals(Utfall.Oppfylt, vurdering.utfall)
    }

    // Motsatt vei gjelder ikke: også vi selv kan lande på en generell kode
    @Test
    fun `saksbehandler kan bruke en generell kodeverkkode`() {
        val vurdering =
            Vilkårsvurdering.avSaksbehandler(
                prøvingId = null,
                vilkår = Vilkår.Opptjening,
                fødselsnummer = FØDSELSNUMMER,
                skjæringstidspunkt = 1.februar,
                kodeverkkode = Kodeverkkode.IKKE_OPPTJENING_ARBEID_ELLER_YTELSE,
                saksbehandlerIdent = "Z999999",
                fritekstbegrunnelse = "vurdert etter dialog med bruker",
                vurdertTidspunkt = Instant.parse("2018-02-01T09:00:00Z"),
            )

        assertEquals(Detaljeringsgrad.GENERELL, vurdering.kodeverkkode.detaljeringsgrad)
        assertEquals(Opphav.Saksbehandler(Vilkår.Opptjening, "Z999999", "vurdert etter dialog med bruker"), vurdering.opphav)
    }

    private fun infotrygdvurdering(kodeverkkode: Kodeverkkode) =
        Vilkårsvurdering.fraInfotrygd(
            vilkår = Vilkår.Opptjening,
            fødselsnummer = FØDSELSNUMMER,
            skjæringstidspunkt = 1.februar,
            kodeverkkode = kodeverkkode,
            vurdertTidspunkt = Instant.parse("2018-02-01T09:00:00Z"),
        )

    private companion object {
        const val FØDSELSNUMMER = "12029240045"
    }
}
