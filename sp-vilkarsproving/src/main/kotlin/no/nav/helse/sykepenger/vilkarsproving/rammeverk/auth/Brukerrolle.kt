package no.nav.helse.sykepenger.vilkarsproving.rammeverk.auth

/** App-definert brukerrolle, implementeres av appens egen enum (f.eks. `AppRolle`). */
interface Brukerrolle {
    val navn: String
}
