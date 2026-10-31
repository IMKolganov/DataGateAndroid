package com.imkolganov.datagate.vpn

/**
 * Android fires [android.net.ConnectivityManager.NetworkCallback.onCapabilitiesChanged]
 * often (validated/signal/bandwidth) while the uplink is unchanged. Those capability-only
 * events (CAP_CHANGED on the default network, UNDERLYING_CAP on the non-VPN uplink)
 * must not enqueue reconnect / health-check / probe work for a healthy session.
 */
internal object VpnNetworkCapChangePolicy {
    fun isCapabilityNoiseSource(source: String): Boolean =
        source == "CAP_CHANGED" || source == "UNDERLYING_CAP"

    /**
     * @return false when a capability-only callback can be ignored.
     * AVAILABLE / LOST / underlying AVAILABLE/LOST always dispatch.
     *
     * Optional [lastUnderlyingHandle] / [currentUnderlyingHandle] let Xray suppress
     * UNDERLYING_CAP when the non-VPN network identity is unchanged. OpenVPN omits
     * them (defaults null) and only filters CAP_CHANGED via transport/usable.
     */
    fun shouldDispatch(
        source: String,
        transport: String,
        usable: Boolean,
        hasActiveSession: Boolean,
        desiredConnection: Boolean,
        lastTransport: String?,
        lastUsable: Boolean?,
        lastUnderlyingHandle: Long? = null,
        currentUnderlyingHandle: Long? = null,
    ): Boolean {
        if (!isCapabilityNoiseSource(source)) return true
        if (desiredConnection && !hasActiveSession) return true
        if (!usable) return true
        if (lastUsable != null && lastUsable != usable) return true
        if (lastTransport != null && lastTransport != transport) return true
        // Real uplink identity change via capability callback (wifi↔cell handle swap).
        if (
            lastUnderlyingHandle != null &&
            currentUnderlyingHandle != null &&
            lastUnderlyingHandle != currentUnderlyingHandle
        ) {
            return true
        }
        // Same uplink, session already up — ignore capability spam.
        return !(hasActiveSession && lastTransport != null && lastUsable == true)
    }
}
