package no.nav.helse.sykepenger.vilkarsproving.application

import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkår
import no.nav.helse.sykepenger.vilkarsproving.domain.Vilkårsprøving
import java.time.LocalDate

/**
 * Lager for pågående prøvinger. Invarianten "kun én aktiv prøving per (vilkår, fødselsnummer,
 * skjæringstidspunkt)" håndheves av lageret selv — i en relasjonsdatabase av et partielt unikt
 * indeks over de aktive tilstandene, ikke av en sjekk i applikasjonskoden.
 *
 * Merk at vilkåret er en del av nøkkelen: en pågående opptjeningsprøving skal ikke hindre at
 * medlemskap prøves samtidig for samme person og skjæringstidspunkt.
 */
internal interface VilkårsprøvingRepository {
    /** Kaster dersom det allerede finnes en aktiv prøving av samme vilkår for samme nøkkel. */
    fun opprett(prøving: Vilkårsprøving)

    fun oppdater(prøving: Vilkårsprøving)

    fun finnSiste(
        vilkår: Vilkår,
        fødselsnummer: String,
        skjæringstidspunkt: LocalDate,
    ): Vilkårsprøving?
}
