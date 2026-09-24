package no.nav.helse.sykepenger.vilkarsproving.domain

internal enum class Kategori {
    Arbeidstaker,
    SelvstendigNæringsdrivende,
}

internal val Opptjeningsgrunnlag.kategori: Kategori
    get() =
        when (this) {
            is Opptjeningsgrunnlag.Arbeidstaker -> Kategori.Arbeidstaker
            Opptjeningsgrunnlag.SelvstendigNæringsdrivende -> Kategori.SelvstendigNæringsdrivende
        }
