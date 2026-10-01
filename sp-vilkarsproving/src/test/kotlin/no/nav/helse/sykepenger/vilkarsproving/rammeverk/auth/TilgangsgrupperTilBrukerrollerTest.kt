package no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TilgangsgrupperTilBrukerrollerTest {
    private enum class TestRolle(
        override val navn: String,
    ) : Brukerrolle {
        Beslutter("beslutter"),
        Saksbehandler("saksbehandler"),
    }

    private val sut = TilgangsgrupperTilBrukerroller(mapOf("beslutter-uuid" to TestRolle.Beslutter))

    @Test
    fun `kjent gruppe-uuid mappes til riktig rolle`() {
        assertEquals(setOf(TestRolle.Beslutter), sut.brukerroller(setOf("beslutter-uuid")))
    }

    @Test
    fun `ukjent gruppe gir tomt rollesett, ikke feil`() {
        assertEquals(emptySet<TestRolle>(), sut.brukerroller(setOf("annen-uuid")))
    }
}
