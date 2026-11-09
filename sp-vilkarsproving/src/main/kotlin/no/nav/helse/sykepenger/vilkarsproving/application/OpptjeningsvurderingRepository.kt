package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Opptjeningsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.OpptjeningsvurderingId
import no.nav.helse.sykepenger.vilkarsproving.domain.RettighetskategoriId
import java.time.Instant
import java.time.LocalDate

internal interface OpptjeningsvurderingRepository {
    fun lagre(vurdering: Opptjeningsvurdering)

    fun gjeldende(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Opptjeningsvurdering?

    fun historikk(
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): List<Opptjeningsvurdering>

    fun finn(opptjeningsvurderingId: OpptjeningsvurderingId): Opptjeningsvurdering?

    /** Registrerer at vurderingen gjelder for rettighetskategori-ID-ene. Koblinger som finnes fra før, beholdes uendret. */
    fun koble(
        opptjeningsvurderingId: OpptjeningsvurderingId,
        rettighetskategoriIder: Set<RettighetskategoriId>,
        registrert: Instant,
    )

    /** Den koblede vurderingen med senest vurderingstidspunkt. */
    fun nyesteKobletTil(rettighetskategoriId: RettighetskategoriId): Opptjeningsvurdering?

    fun rettighetskategoriIder(opptjeningsvurderingId: OpptjeningsvurderingId): Set<RettighetskategoriId>

    /** Lagrer en vurdering som erstatter [erstattet]. Den nye vurderingen gjelder for de samme rettighetskategori-ID-ene. */
    fun lagreErstatning(
        vurdering: Opptjeningsvurdering,
        erstattet: Opptjeningsvurdering?,
    ) {
        lagre(vurdering)
        if (erstattet == null) return
        koble(vurdering.id, rettighetskategoriIder(erstattet.id), vurdering.vurdertTidspunkt)
    }
}
