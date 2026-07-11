package com.imkolganov.datagate.vpn

data class CachedStatusRestoreResult(
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
            cachedName.equals("DISCONNECTING", ignoreCase = true) ||
            cachedName.equals("PAUSED", ignoreCase = true) ||
            cachedName.equals("RESUMED", ignoreCase = true) ||
            cachedName.equals("RECONNECTING", ignoreCase = true) ||
            cachedName.equals("WAITING_NETWORK", ignoreCase = true)

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

    /**
     * WSS bridge loss is only actionable while the service still considers the tunnel up.
     * Ignores teardown during pause/disconnect and pre-CONNECTED connect attempts.
     */
    fun shouldHandleBridgeTransportLost(
        isStopping: Boolean,
        desiredConnection: Boolean,
        isPaused: Boolean,
        hasActiveSession: Boolean
    ): Boolean {
        return !isStopping && desiredConnection && !isPaused && hasActiveSession
    }

    fun shouldReconnectAfterBridgeTransportLost(
        reconnectPendingAfterJob: Boolean,
        desiredConnection: Boolean,
        isStopping: Boolean,
        isPaused: Boolean
    ): Boolean {
        return reconnectPendingAfterJob && desiredConnection && !isStopping && !isPaused
    }

    /**
     * While [connect] blocks the single native executor thread, another thread must not
     * [java.util.concurrent.Future.get] on a queued [stop] — that deadlocks disconnect.
     */
    fun shouldAwaitNativeStopOnCallerThread(
        runsOnNativeThread: Boolean,
        nativeVpnJobActive: Boolean,
    ): Boolean = runsOnNativeThread || !nativeVpnJobActive

    /**
     * While [connect] holds [OvpnNativeThread], pause/resume must use a foreign thread so
     * ovpncli can [thread_safe_pause] into the running io_context.
     */
    fun shouldSchedulePauseResumeOnForeignThread(nativeVpnJobActive: Boolean): Boolean = true

    /** Guard: scheduling pause/resume on the native executor behind connect always deadlocks. */
    fun mustNotSchedulePauseResumeOnNativeExecutor(scheduledOnNativeExecutor: Boolean): Boolean =
        !scheduledOnNativeExecutor
}
