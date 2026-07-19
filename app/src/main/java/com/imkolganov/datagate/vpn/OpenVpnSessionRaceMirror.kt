package com.imkolganov.datagate.vpn

/**
 * Testable mirror of the [OpenVpn3Service.startVpn] / DISCONNECTED / bridge-loss finally race.
 * Tracks fake globals the way the service does — without VpnService or native connect().
 */
internal class OpenVpnSessionRaceMirror {
    var generation = 0
        private set
    var ownerGeneration: Int? = null
        private set
    var tornDownGeneration: Int? = null
        private set
    var reconnectPendingAfterJob = false
    var reconnectFromFinallyCount = 0
        private set
    var reconnectFromDisconnectedCount = 0
        private set

    /** Mimics startVpn: bump gen, claim globals, return session token for finally. */
    fun startSession(): Int {
        val session = ++generation
        ownerGeneration = session
        return session
    }

    fun onBridgeTransportLost() {
        reconnectPendingAfterJob = true
    }

    /**
     * Core DISCONNECTED path: either defer to bridge-loss finally, or start a replacement
     * session (the bug when both paths fire).
     */
    fun onCoreDisconnected(desiredConnection: Boolean = true): Boolean {
        if (!desiredConnection) return false
        if (OpenVpnSessionTeardownPolicy.shouldDeferReconnectToBridgeLossFinally(reconnectPendingAfterJob)) {
            return false
        }
        reconnectFromDisconnectedCount++
        startSession()
        return true
    }

    /**
     * vpnJob.finally for [sessionGeneration]. Returns whether teardown + reconnect ran.
     */
    fun runFinally(sessionGeneration: Int): FinallyOutcome {
        if (!OpenVpnSessionTeardownPolicy.shouldRunVpnJobFinally(sessionGeneration, generation)) {
            return FinallyOutcome.SKIPPED_STALE
        }
        tornDownGeneration = sessionGeneration
        if (ownerGeneration == sessionGeneration) {
            ownerGeneration = null
        }
        val shouldReconnect = reconnectPendingAfterJob
        reconnectPendingAfterJob = false
        if (OpenVpnRuntimePolicy.shouldReconnectAfterBridgeTransportLost(
                reconnectPendingAfterJob = shouldReconnect,
                desiredConnection = true,
                isStopping = false,
                isPaused = false,
            )
        ) {
            reconnectFromFinallyCount++
            startSession()
            return FinallyOutcome.TEARDOWN_AND_RECONNECT
        }
        return FinallyOutcome.TEARDOWN_ONLY
    }

    enum class FinallyOutcome {
        SKIPPED_STALE,
        TEARDOWN_ONLY,
        TEARDOWN_AND_RECONNECT,
    }
}
