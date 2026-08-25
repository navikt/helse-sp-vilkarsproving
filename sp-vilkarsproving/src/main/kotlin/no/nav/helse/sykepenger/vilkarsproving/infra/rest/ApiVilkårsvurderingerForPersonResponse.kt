@file:UseContextualSerialization(UUID::class, Instant::class, LocalDate::class)

package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseContextualSerialization
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serializable
internal data class ApiVilkårsvurderingerForPersonResponse(
    val skjæringstidspunkt: LocalDate,
    val krav: List<ApiKravvurdering>,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kravkilde", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApiKravvurdering.Vurdert::class, name = "VURDERT_I_SPEIL"),
    JsonSubTypes.Type(value = ApiKravvurdering.OverførtFraInfotrygd::class, name = "OVERFOERT_FRA_INFOTRYGD"),
)
@Serializable
internal sealed interface ApiKravvurdering {
    val id: UUID
    val kravkode: ApiKravkode
    val utfall: ApiUtfall

    val kravkilde: ApiKravkilde

    @Serializable
    data class Vurdert(
        override val id: UUID,
        override val kravkode: ApiKravkode,
        override val utfall: ApiUtfall,
        val avgjørendeVilkårskode: ApiVilkårskode,
        val vurderinger: List<ApiVilkårsvurdering>,
    ) : ApiKravvurdering {
        override val kravkilde: ApiKravkilde = ApiKravkilde.VURDERT_I_SPEIL

        init {
            require(vurderinger.isNotEmpty()) { "En kravvurdering gjort hos oss må ha minst én vilkårsvurdering" }
            require(vurderinger.any { it.vilkårskode == avgjørendeVilkårskode }) {
                "Det avgjørende vilkåret $avgjørendeVilkårskode må finnes i stien"
            }
        }
    }

    @Serializable
    data class OverførtFraInfotrygd(
        override val id: UUID,
        override val kravkode: ApiKravkode,
        override val utfall: ApiUtfall,
    ) : ApiKravvurdering {
        override val kravkilde: ApiKravkilde = ApiKravkilde.OVERFOERT_FRA_INFOTRYGD
    }
}

@Serializable
internal enum class ApiKravkilde {
    VURDERT_I_SPEIL,
    OVERFOERT_FRA_INFOTRYGD,
}

@Serializable
internal data class ApiVilkårsvurdering(
    val id: UUID,
    val vilkårskode: ApiVilkårskode,
    val utfall: ApiUtfall,
    val vurdertTidspunkt: Instant?,
    val kilde: ApiVurderingskilde,
)

@Serializable
internal enum class ApiUtfall {
    OPPFYLT,
    IKKE_OPPFYLT,
}

@Serializable
internal enum class ApiKravkode {
    OPPTJENING,
}

@Serializable
internal enum class ApiVilkårskode {
    OPPTJENING_ARBEID_MINST_4_UKER,

    OPPTJENING_LIKESTILT_YTELSE,

    OPPTJENING_UNNTAK_FORELDREPENGER_UTEN_FORUTGAAENDE_AAP,

    OPPTJENING_YRKESAKTIV_FOER_FORELDREPENGER,
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kildetype", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApiVurderingskilde.Automatisk::class, name = "AUTOMATISK"),
    JsonSubTypes.Type(value = ApiVurderingskilde.Saksbehandler::class, name = "SAKSBEHANDLER"),
    JsonSubTypes.Type(value = ApiVurderingskilde.OverførtFraSpleis::class, name = "OVERFOERT_FRA_SPLEIS"),
)
@Serializable
internal sealed interface ApiVurderingskilde {
    val kildetype: ApiKildetype

    @Serializable
    data class Automatisk(
        val versjonAvKildekode: String,
        val grunnlag: ApiVurderingsgrunnlag,
    ) : ApiVurderingskilde {
        override val kildetype: ApiKildetype = ApiKildetype.AUTOMATISK
    }

    @Serializable
    data class Saksbehandler(
        val ident: String,
        val fritekstbegrunnelse: String,
    ) : ApiVurderingskilde {
        override val kildetype: ApiKildetype = ApiKildetype.SAKSBEHANDLER
    }

    @Serializable
    data class OverførtFraSpleis(
        val grunnlag: ApiVurderingsgrunnlag,
    ) : ApiVurderingskilde {
        override val kildetype: ApiKildetype = ApiKildetype.OVERFOERT_FRA_SPLEIS
    }
}

@Serializable
internal enum class ApiKildetype {
    AUTOMATISK,
    SAKSBEHANDLER,
    OVERFOERT_FRA_SPLEIS,
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "grunnlagstype", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApiVurderingsgrunnlag.Arbeidsforhold::class, name = "ARBEIDSFORHOLD"),
    JsonSubTypes.Type(value = ApiVurderingsgrunnlag.SelvstendigNæringsdrivende::class, name = "SELVSTENDIG_NAERINGSDRIVENDE"),
)
@Serializable
internal sealed interface ApiVurderingsgrunnlag {
    val grunnlagstype: ApiGrunnlagstype

    @Serializable
    data class Arbeidsforhold(
        val arbeidsforhold: List<ApiArbeidsforhold>,
        val opptjeningsperiode: ApiPeriode?,
        val opptjeningsdager: Int,
    ) : ApiVurderingsgrunnlag {
        override val grunnlagstype: ApiGrunnlagstype = ApiGrunnlagstype.ARBEIDSFORHOLD
    }

    @Serializable
    data class SelvstendigNæringsdrivende(
        override val grunnlagstype: ApiGrunnlagstype = ApiGrunnlagstype.SELVSTENDIG_NAERINGSDRIVENDE,
    ) : ApiVurderingsgrunnlag {
        init {
            require(grunnlagstype == ApiGrunnlagstype.SELVSTENDIG_NAERINGSDRIVENDE) { "Diskriminatoren må stemme med varianten" }
        }
    }
}

@Serializable
internal enum class ApiGrunnlagstype {
    ARBEIDSFORHOLD,
    SELVSTENDIG_NAERINGSDRIVENDE,
}

@Serializable
internal data class ApiPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

@Serializable
internal data class ApiArbeidsforhold(
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate?,
    val type: ApiArbeidsforholdtype,
)

@Serializable
internal enum class ApiArbeidsforholdtype {
    FORENKLET_OPPGJØRSORDNING,
    FRILANSER,
    MARITIMT,
    ORDINÆRT,
}
