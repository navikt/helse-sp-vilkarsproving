package no.nav.helse.sykepenger.vilkarsproving.rammeverk.rest

import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Brukerrolle
import no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth.Tilgang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RestBehandlerTest {
    private enum class TestRolle(
        override val navn: String,
    ) : Brukerrolle {
        Beslutter("beslutter"),
    }

    private class HentTingBehandler : GetBehandler<Unit, String, RammeverkFeilkode, TestRolle, Unit> {
        override val påkrevdTilgang = Tilgang.Les
        override val tag = "ting"

        override fun behandle(
            resource: Unit,
            kallKontekst: KallKontekst<Unit, TestRolle>,
        ) = RestResponse.ok("ok")
    }

    @Test
    fun `operationId utledes fra klassenavn med liten forbokstav`() {
        assertEquals("hentTingBehandler", HentTingBehandler().operationIdBasertPåKlassenavn())
    }

    @Test
    fun `paakrevdeBrukerroller er tomt som default`() {
        assertEquals(emptySet<TestRolle>(), HentTingBehandler().påkrevdeBrukerroller)
    }
}

class RestResponseTest {
    @Test
    fun `ok wrapper body`() {
        val response = RestResponse.ok("hello")
        assertEquals(RestResponse.Ok("hello"), response)
    }

    @Test
    fun `feil wrapper feilkode og detalj`() {
        val response = RestResponse.feil(RammeverkFeilkode.ManglerTilgang, "ingen tilgang")
        assertEquals(RestResponse.Feil(RammeverkFeilkode.ManglerTilgang, "ingen tilgang"), response)
    }
}
