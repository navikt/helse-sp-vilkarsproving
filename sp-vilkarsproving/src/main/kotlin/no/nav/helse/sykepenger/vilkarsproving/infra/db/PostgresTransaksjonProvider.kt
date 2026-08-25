package no.nav.helse.sykepenger.vilkarsproving.infra.db

import kotliquery.Session
import kotliquery.sessionOf
import no.nav.helse.speil.backend.app.rest.TransaksjonProvider
import no.nav.helse.sykepenger.vilkarsproving.application.Transaksjonskontekst
import javax.sql.DataSource

/**
 * Kjører arbeidet i én databasetransaksjon: kaster [block] rulles alt tilbake, ellers commites alt.
 */
internal class PostgresTransaksjonProvider(
    private val dataSource: DataSource,
) : TransaksjonProvider<Transaksjonskontekst> {
    override fun <T> transaksjon(block: (Transaksjonskontekst) -> T): T =
        sessionOf(dataSource).use { session ->
            session.transaction { transaksjon -> block(PostgresTransaksjonskontekst(transaksjon)) }
        }
}

private class PostgresTransaksjonskontekst(
    session: Session,
) : Transaksjonskontekst {
    override val kravprøvinger = PostgresKravprøvingRepository(session)
    override val kravvurderinger = PostgresKravvurderingRepository(session)
}
