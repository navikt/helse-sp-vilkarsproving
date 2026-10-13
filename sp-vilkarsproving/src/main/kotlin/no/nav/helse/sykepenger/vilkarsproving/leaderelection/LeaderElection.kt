package no.nav.helse.sykepenger.vilkarsproving.leaderelection

internal fun interface LeaderElection {
    fun isLeader(): Boolean
}
