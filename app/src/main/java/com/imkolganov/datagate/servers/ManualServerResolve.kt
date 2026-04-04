package com.imkolganov.datagate.servers

/**
 * Result of resolving a user-picked server for in-app (WSS) VPN connect.
 */
sealed class ManualServerResolve {
    data class Ok(val result: BestServerResult) : ManualServerResolve()

    /** Unknown id, offline, or missing from API. */
    data object NotAvailable : ManualServerResolve()

    /**
     * Server is online but [com.imkolganov.datagate.model.servers.OpenVpnServer.isEnableWss] is false;
     * our tunnel requires WSS — user must use external OpenVPN Connect.
     */
    data class RequiresExternalOpenVpn(val serverName: String?) : ManualServerResolve()

    /** Server exists and is online but user's quota plan does not include it. */
    data class QuotaPlanBlocked(val serverName: String?) : ManualServerResolve()
}
