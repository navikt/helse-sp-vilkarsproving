package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.domain.VurderingId
import java.time.LocalDate

/**
 * Lager for ferdige vurderinger. Vurderinger skrives kun én gang og oppdateres aldri; en ny prøving
 * gir en ny vurdering, slik at historikken består.
 *
 * Oppslag er alltid på et konkret vilkår — en opptjeningsvurdering og en medlemskapsvurdering på
 * samme skjæringstidspunkt er to uavhengige resultater.
 */
internal interface VilkårsvurderingRepository {
    fun lagre(vurdering: Vilkårsvurdering)

    fun gjeldende(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Vilkårsvurdering?

    fun finn(
        vilkår: Vilkår,
        vurderingId: VurderingId,
    ): Vilkårsvurdering?

    fun finnAlle(fødselsnummer: String): List<Vilkårsvurdering>
}
