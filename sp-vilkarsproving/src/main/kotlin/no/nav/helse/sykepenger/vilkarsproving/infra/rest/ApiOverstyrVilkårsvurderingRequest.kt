@file:UseContextualSerialization(LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.LocalDate

/**
 * Request-bodyen til [ApiOverstyrVilkårsvurderingResource]. Gjenbruker [ApiVilkårskode]/[ApiUtfall]
 * fra [ApiVilkårsvurderingerForPersonResponse]-filen — samme kodeverk brukes i request og respons.
 *
 * Det finnes bevisst ikke noe eget strukturert "årsakskode"-felt: [vilkårskode] ER den strukturerte
 * koden for *hva* saksbehandler overstyrer, mens [fritekstbegrunnelse] er *hvorfor* saksbehandler
 * mener utfallet skal være annerledes enn det automatikken kom til — en begrunnelse som pr. natur
 * varierer fra sak til sak, og som derfor ikke egner seg som et lukket kodeverk.
 */
@Serializable
internal data class ApiOverstyrVilkårsvurderingRequest(
    val skjæringstidspunkt: LocalDate,
    val vilkårskode: ApiVilkårskode,
    val utfall: ApiUtfall,
    val fritekstbegrunnelse: String,
)
