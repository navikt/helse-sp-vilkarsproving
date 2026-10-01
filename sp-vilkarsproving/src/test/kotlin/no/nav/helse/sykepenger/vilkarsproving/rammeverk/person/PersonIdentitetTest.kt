package no.nav.helse.sykepenger.vilkarsproving.rammeverk.person

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class IdentitetsnummerTest {
    @Test
    fun `godtar 11 siffer`() {
        Identitetsnummer("12345678901")
    }

    @Test
    fun `avviser feil lengde`() {
        assertThrows(IllegalArgumentException::class.java) { Identitetsnummer("123") }
    }

    @Test
    fun `toString lekker aldri verdien`() {
        val ident = Identitetsnummer("12345678901")
        assertEquals(false, ident.toString().contains("12345678901"))
    }
}

class PersonPseudoIdTest {
    @Test
    fun `fraString parser gyldig uuid`() {
        val uuid = UUID.randomUUID()
        assertEquals(PersonPseudoId(uuid), PersonPseudoId.fraString(uuid.toString()))
    }

    @Test
    fun `fraString gir null for ugyldig input, ikke feil`() {
        assertNull(PersonPseudoId.fraString("ikke-en-uuid"))
    }
}
