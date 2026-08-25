package no.nav.helse.sykepenger.vilkarsproving.rest

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiKravkode
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiKravvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiUtfall
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiVilkårskode
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiVilkårsvurdering
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiVurderingsgrunnlag
import no.nav.helse.sykepenger.vilkarsproving.infra.rest.ApiVurderingskilde
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ApiDiskriminatorTest {
    @Test
    fun `ApiKravvurdering - navn i JsonSubTypes stemmer med faktisk kravkilde`() {
        verifiserDiskriminator(
            ApiKravvurdering::class.java,
            ApiKravvurdering.VurdertISpeil(
                id = UUID.randomUUID(),
                kravkode = ApiKravkode.OPPTJENING,
                rettTilSykepenger = true,
                avgjørendeVilkårskode = ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                vurderinger =
                    listOf(
                        ApiVilkårsvurdering(
                            id = UUID.randomUUID(),
                            vilkårskode = ApiVilkårskode.OPPTJENING_ARBEID_MINST_4_UKER,
                            utfall = ApiUtfall.OPPFYLT,
                            vurdertTidspunkt = Instant.now(),
                            kilde =
                                ApiVurderingskilde.Automatisk(
                                    versjonAvKildekode = "1",
                                    grunnlag = ApiVurderingsgrunnlag.SelvstendigNæringsdrivende(),
                                ),
                        ),
                    ),
            ),
            ApiKravvurdering.OverførtFraInfotrygd(
                id = UUID.randomUUID(),
                kravkode = ApiKravkode.OPPTJENING,
                rettTilSykepenger = true,
            ),
        )
    }

    @Test
    fun `ApiVurderingskilde - navn i JsonSubTypes stemmer med faktisk kildetype`() {
        verifiserDiskriminator(
            ApiVurderingskilde::class.java,
            ApiVurderingskilde.Automatisk(
                versjonAvKildekode = "1",
                grunnlag = ApiVurderingsgrunnlag.SelvstendigNæringsdrivende(),
            ),
            ApiVurderingskilde.Saksbehandler(
                ident = "Z999999",
                fritekstbegrunnelse = "en forklaring",
            ),
            ApiVurderingskilde.OverførtFraSpleis(
                grunnlag = ApiVurderingsgrunnlag.SelvstendigNæringsdrivende(),
            ),
        )
    }

    @Test
    fun `ApiVurderingsgrunnlag - navn i JsonSubTypes stemmer med faktisk grunnlagstype`() {
        verifiserDiskriminator(
            ApiVurderingsgrunnlag::class.java,
            ApiVurderingsgrunnlag.Arbeidsforhold(
                arbeidsforhold = emptyList(),
                opptjeningsperiode = null,
                opptjeningsdager = 0,
            ),
            ApiVurderingsgrunnlag.SelvstendigNæringsdrivende(),
        )
    }

    private fun verifiserDiskriminator(
        unionstype: Class<*>,
        vararg instanser: Any,
    ) {
        val typeInfo = unionstype.getAnnotation(JsonTypeInfo::class.java) ?: error("Mangler @JsonTypeInfo på $unionstype")
        val subTypes = unionstype.getAnnotation(JsonSubTypes::class.java) ?: error("Mangler @JsonSubTypes på $unionstype")
        val forventetNavnPerKlasse = subTypes.value.associate { it.value.java to it.name }
        val gettermetode = "get" + typeInfo.property.replaceFirstChar { it.uppercase() }

        instanser.forEach { instans ->
            val forventetNavn =
                forventetNavnPerKlasse[instans::class.java]
                    ?: error("Ingen @JsonSubTypes.Type registrert for ${instans::class.java} i $unionstype")
            val faktiskVerdi =
                instans::class.java
                    .getMethod(gettermetode)
                    .invoke(instans)
                    .toString()

            assertEquals(forventetNavn, faktiskVerdi) {
                "@JsonSubTypes sier ${instans::class.simpleName} skal skrive ${typeInfo.property}=$forventetNavn, " +
                    "men instansen har $faktiskVerdi"
            }
        }
    }
}
