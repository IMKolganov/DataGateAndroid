package com.imkolganov.datagate.servers

/**
 * Result of resolving a user-picked server for in-app VPN connect.
 */
sealed class ManualServerResolve {
    data class Ok(val result: BestServerResult) : ManualServerResolve()

    /** Unknown id, offline, or missing from API. */
    data object NotAvailable : ManualServerResolve()

    /** Unknown or future server type — not connectable in this app. */
    data class RequiresUnsupportedServerType(val serverName: String?) : ManualServerResolve()

    /** Server exists and is online but user's quota plan does not include it. */
    data class QuotaPlanBlocked(val serverName: String?) : ManualServerResolve()
}
