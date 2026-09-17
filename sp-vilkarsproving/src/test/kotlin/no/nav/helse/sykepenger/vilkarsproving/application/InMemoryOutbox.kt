package no.nav.helse.sykepenger.vilkarsproving.application

internal class InMemoryOutbox : Outbox {
    private val meldinger = mutableMapOf<OutboxMeldingId, UtgåendeLøsning>()
    private val publiserte = mutableSetOf<OutboxMeldingId>()

    internal val upubliserteMeldinger: List<UtgåendeLøsning> get() = meldinger.values.filterNot { it.id in publiserte }

    override fun leggTil(melding: UtgåendeLøsning) {
        meldinger[melding.id] = melding
    }

    override fun hentUpubliserte(maksAntall: Int): List<UtgåendeLøsning> = upubliserteMeldinger.take(maksAntall)

    override fun markerSomPublisert(id: OutboxMeldingId) {
        publiserte.add(id)
    }
}
