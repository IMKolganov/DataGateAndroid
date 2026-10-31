package com.imkolganov.datagate.vpn

/**
 * When the controller must drop [KEY_ACTIVE_ENGINE] so a dead session cannot keep
 * filtering peer-engine broadcasts forever.
 */
object VpnActiveEnginePolicy {
    fun shouldClearActiveEngine(eventName: String, fromQuery: Boolean): Boolean {
        if (fromQuery) return false
        return when (eventName.trim().uppercase()) {
            "ERROR",
            "TUN_SETUP_FAILED",
            "DISCONNECTED" -> true
            else -> false
        }
    }
}
