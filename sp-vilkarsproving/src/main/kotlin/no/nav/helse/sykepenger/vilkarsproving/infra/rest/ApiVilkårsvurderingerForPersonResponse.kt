package no.nav.helse.sykepenger.vilkarsproving.infra.rest

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal data class ApiVilkårsvurderingerForPersonResponse(
    val skjæringstidspunkt: LocalDate,
    val krav: List<ApiOpptjeningsvurdering>,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kravkilde", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApiOpptjeningsvurdering.VurdertISpVilkarproving::class, name = "VURDERT_I_SP_VILKARSPROVING"),
    JsonSubTypes.Type(value = ApiOpptjeningsvurdering.OverførtFraSpleis::class, name = "OVERFORT_FRA_SPLEIS"),
    JsonSubTypes.Type(value = ApiOpptjeningsvurdering.OverførtFraInfotrygd::class, name = "OVERFOERT_FRA_INFOTRYGD"),
)
internal sealed interface ApiOpptjeningsvurdering {
    val id: UUID
    val kravkode: ApiKravkode
    val opptjeningOk: Boolean

    val kravkilde: ApiKravkilde

    data class VurdertISpVilkarproving(
        override val id: UUID,
        override val kravkode: ApiKravkode,
        override val opptjeningOk: Boolean,
        val avgjørendeVilkårskode: ApiVilkårskode?,
        val vurderinger: List<ApiVilkårsvurdering>,
    ) : ApiOpptjeningsvurdering {
        override val kravkilde: ApiKravkilde = ApiKravkilde.VURDERT_I_SP_VILKARSPROVING
    }

    data class OverførtFraSpleis(
        override val id: UUID,
        override val kravkode: ApiKravkode,
        override val opptjeningOk: Boolean,
        val avgjørendeVilkårskode: ApiVilkårskode?,
        val vurderinger: List<ApiVilkårsvurdering>,
    ) : ApiOpptjeningsvurdering {
        override val kravkilde: ApiKravkilde = ApiKravkilde.OVERFORT_FRA_SPLEIS
    }

    data class OverførtFraInfotrygd(
        override val id: UUID,
        override val kravkode: ApiKravkode,
        override val opptjeningOk: Boolean,
    ) : ApiOpptjeningsvurdering {
        override val kravkilde: ApiKravkilde = ApiKravkilde.OVERFOERT_FRA_INFOTRYGD
    }
}

internal enum class ApiKravkilde {
    VURDERT_I_SP_VILKARSPROVING,
    OVERFORT_FRA_SPLEIS,
    OVERFOERT_FRA_INFOTRYGD,
}

internal data class ApiVilkårsvurdering(
    val id: UUID,
    val vilkårskode: ApiVilkårskode,
    val utfall: ApiUtfall,
    val vurdertTidspunkt: Instant,
    val lovreferanse: ApiLovreferanse,
    val kilde: ApiVurderingskilde,
)

internal data class ApiLovreferanse(
    val lov: String,
    val paragraf: String,
    val avsnitt: Int?,
    val setning: Int?,
    val bokstav: String?,
    val iKraftFra: LocalDate,
)

internal enum class ApiUtfall {
    OPPFYLT,
    IKKE_OPPFYLT,
}

internal enum class ApiKravkode {
    OPPTJENING,
}

internal enum class ApiVilkårskode {
    OPPTJENING_ARBEID_MINST_4_UKER,
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "kildetype", visible = true)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApiVurderingskilde.Automatisk::class, name = "AUTOMATISK"),
    JsonSubTypes.Type(value = ApiVurderingskilde.Saksbehandler::class, name = "SAKSBEHANDLER"),
    JsonSubTypes.Type(value = ApiVurderingskilde.OverførtFraSpleis::class, name = "OVERFOERT_FRA_SPLEIS"),
)
internal sealed interface ApiVurderingskilde {
    val kildetype: ApiKildetype

    data class Automatisk(
        val versjonAvKildekode: String,
        val grunnlag: ApiVurderingsgrunnlag,
    ) : ApiVurderingskilde {
        override val kildetype: ApiKildetype = ApiKildetype.AUTOMATISK
    }

    data class Saksbehandler(
        val ident: String,
        val fritekstbegrunnelse: String,
        val journalpostId: List<String> = emptyList(),
    ) : ApiVurderingskilde {
        override val kildetype: ApiKildetype = ApiKildetype.SAKSBEHANDLER
    }

    data class OverførtFraSpleis(
        val grunnlag: ApiVurderingsgrunnlag,
    ) : ApiVurderingskilde {
        override val kildetype: ApiKildetype = ApiKildetype.OVERFOERT_FRA_SPLEIS
    }
}

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
internal sealed interface ApiVurderingsgrunnlag {
    val grunnlagstype: ApiGrunnlagstype

    data class Arbeidsforhold(
        val arbeidsforhold: List<ApiArbeidsforhold>,
        val opptjeningsperiode: ApiPeriode?,
        val opptjeningsdager: Int,
    ) : ApiVurderingsgrunnlag {
        override val grunnlagstype: ApiGrunnlagstype = ApiGrunnlagstype.ARBEIDSFORHOLD
    }

    data class SelvstendigNæringsdrivende(
        override val grunnlagstype: ApiGrunnlagstype = ApiGrunnlagstype.SELVSTENDIG_NAERINGSDRIVENDE,
    ) : ApiVurderingsgrunnlag {
        init {
            require(grunnlagstype == ApiGrunnlagstype.SELVSTENDIG_NAERINGSDRIVENDE) { "Diskriminatoren må stemme med varianten" }
        }
    }
}

internal enum class ApiGrunnlagstype {
    ARBEIDSFORHOLD,
    SELVSTENDIG_NAERINGSDRIVENDE,
}

internal data class ApiPeriode(
    val fom: LocalDate,
    val tom: LocalDate,
)

internal data class ApiArbeidsforhold(
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate?,
    val type: ApiArbeidsforholdtype,
)

internal enum class ApiArbeidsforholdtype {
    FORENKLET_OPPGJØRSORDNING,
    FRILANSER,
    MARITIMT,
    ORDINÆRT,
    UKJENT,
}
