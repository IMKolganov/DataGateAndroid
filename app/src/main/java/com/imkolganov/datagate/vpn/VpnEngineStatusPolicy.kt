package com.imkolganov.datagate.vpn

/**
 * Both OpenVPN and Xray publish on [OpenVpn3Service.ACTION_STATUS]. While one engine is
 * active, late/peer teardown events from the other must not rewrite UI session state.
 */
object VpnEngineStatusPolicy {

    /**
     * @param activeEngineName value of the controller's active-engine pref, or null after
     *   user disconnect / before any connect (accept everything).
     * @param eventEngineName [OpenVpn3Service.EXTRA_STATUS_ENGINE] on the broadcast, or null
     *   for legacy/untagged events (accept for backward compatibility).
     */
    fun shouldApplyStatusBroadcast(
        activeEngineName: String?,
        eventEngineName: String?,
    ): Boolean {
        if (activeEngineName.isNullOrBlank()) return true
        if (eventEngineName.isNullOrBlank()) return true
        return activeEngineName.equals(eventEngineName, ignoreCase = true)
    }
}
