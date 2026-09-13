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
        lastEventName: String,
        lastEventInfo: String,
        disconnectedInfo: String,
        connectedInfo: String,
        connectingInfo: String,
    ): Pair<String, String> {
        if (stopping) return "DISCONNECTED" to disconnectedInfo
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
