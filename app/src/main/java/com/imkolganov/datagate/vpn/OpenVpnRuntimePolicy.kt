package com.imkolganov.datagate.vpn

internal data class CachedStatusRestoreResult(
    val eventName: String?,
    val eventInfo: String?,
    val shouldPersist: Boolean
)

internal object OpenVpnRuntimePolicy {
    fun restoreCachedStatus(cachedName: String?, cachedInfo: String?): CachedStatusRestoreResult {
        if (cachedName.isNullOrBlank()) {
            return CachedStatusRestoreResult(
                eventName = null,
                eventInfo = null,
                shouldPersist = false
            )
        }

        val wasActiveState = cachedName.equals("CONNECTED", ignoreCase = true) ||
            cachedName.equals("CONNECTING", ignoreCase = true) ||
            cachedName.equals("DISCONNECTING", ignoreCase = true)

        return if (wasActiveState) {
            CachedStatusRestoreResult(
                eventName = "DISCONNECTED",
                eventInfo = "Session restored after process restart",
                shouldPersist = true
            )
        } else {
            CachedStatusRestoreResult(
                eventName = cachedName,
                eventInfo = cachedInfo ?: "",
                shouldPersist = false
            )
        }
    }

    fun canAttemptReconnect(
        nowMs: Long,
        lastAttemptAtMs: Long,
        backoffMs: Long,
        enforceBackoff: Boolean
    ): Boolean {
        if (!enforceBackoff) return true
        return nowMs - lastAttemptAtMs >= backoffMs
    }

    /** Idle service query must not wipe in-flight connect UI with DISCONNECTED. */
    fun shouldIgnoreIdleQueryDisconnected(
        fromQuery: Boolean,
        eventName: String,
        isConnectRequested: Boolean,
        isVpnConnected: Boolean
    ): Boolean {
        return fromQuery &&
            eventName.equals("DISCONNECTED", ignoreCase = true) &&
            isConnectRequested &&
            !isVpnConnected
    }
}
