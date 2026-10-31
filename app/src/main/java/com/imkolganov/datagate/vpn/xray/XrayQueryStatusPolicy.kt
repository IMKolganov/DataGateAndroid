package com.imkolganov.datagate.vpn.xray

/**
 * QUERY_STATUS must reflect the live TUN/core, not a stale CONNECTED cache after the
 * process or session died.
 */
internal object XrayQueryStatusPolicy {
    fun resolve(
        running: Boolean,
        hasTun: Boolean,
        stopping: Boolean,
        paused: Boolean = false,
        lastEventName: String,
        lastEventInfo: String,
        disconnectedInfo: String,
        connectedInfo: String,
        connectingInfo: String,
        pausedInfo: String = connectedInfo,
    ): Pair<String, String> {
        if (stopping && !paused) return "DISCONNECTED" to disconnectedInfo
        if (paused) return "PAUSED" to pausedInfo
        if (running && hasTun) return "CONNECTED" to connectedInfo
        return when (lastEventName.trim().uppercase()) {
            "CONNECTING" -> "CONNECTING" to lastEventInfo.ifBlank { connectingInfo }
            "WAITING_NETWORK" -> "WAITING_NETWORK" to lastEventInfo.ifBlank { connectingInfo }
            "RECONNECTING" -> "RECONNECTING" to lastEventInfo.ifBlank { connectingInfo }
            "ERROR" -> "ERROR" to lastEventInfo
            else -> "DISCONNECTED" to disconnectedInfo
        }
    }
}
