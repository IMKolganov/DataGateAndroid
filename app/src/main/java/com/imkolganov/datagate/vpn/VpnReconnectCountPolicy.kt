package com.imkolganov.datagate.vpn

/**
 * Counts RECONNECTING cycles within one user connection so Home can show
 * reconnect / estimated server-session churn (initial connect + each reconnect).
 */
internal object VpnReconnectCountPolicy {
    data class Snapshot(
        val reconnectCount: Int = 0,
        val cycleActive: Boolean = false,
    )

    fun onEvent(previous: Snapshot, eventName: String): Snapshot {
        return when (eventName.trim().uppercase()) {
            "RECONNECTING" -> {
                if (previous.cycleActive) {
                    previous
                } else {
                    Snapshot(
                        reconnectCount = previous.reconnectCount + 1,
                        cycleActive = true,
                    )
                }
            }
            "CONNECTED", "PAUSED" -> previous.copy(cycleActive = false)
            "DISCONNECTED",
            "DISCONNECTING",
            "ERROR",
            "TUN_SETUP_FAILED",
            "SELECTING_SERVER" -> Snapshot()
            else -> previous
        }
    }

    /** Initial connect + each completed reconnect cycle while the user session is up. */
    fun estimatedSessions(reconnectCount: Int, sessionActive: Boolean): Int =
        if (sessionActive) reconnectCount + 1 else 0
}
