package com.imkolganov.datagate.vpn.xray

/** When Xray should wait, reconnect, or warn after a network / health change. */
internal object XrayNetworkPolicy {
    /**
     * Slow phones often spend 10–30s in establish + [XrayCoreFacade.runFromJson].
     * Network callbacks during that window must not start another connect.
     */
    const val UNDERLYING_SWITCH_SETTLE_MS = 5_000L
    const val UNDERLYING_SWITCH_DEBOUNCE_MS = 8_000L

    fun hasUsableNetwork(hasInternet: Boolean, validated: Boolean): Boolean =
        hasInternet && validated

    fun shouldReconnect(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        paused: Boolean = false,
        connectInFlight: Boolean = false,
    ): Boolean = desiredConnection &&
        !stopping &&
        !paused &&
        !connectInFlight &&
        !running &&
        networkAvailable

    fun shouldWaitForNetwork(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        paused: Boolean = false,
        connectInFlight: Boolean = false,
    ): Boolean = desiredConnection &&
        !stopping &&
        !paused &&
        !connectInFlight &&
        !running &&
        !networkAvailable

    fun shouldRestartUnhealthySession(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        coreRunning: Boolean,
        networkAvailable: Boolean,
        paused: Boolean = false,
        connectInFlight: Boolean = false,
    ): Boolean = desiredConnection &&
        !stopping &&
        !paused &&
        !connectInFlight &&
        running &&
        !coreRunning &&
        networkAvailable

    fun shouldWarnMissingVpnTransport(running: Boolean, hasVpnTransport: Boolean): Boolean =
        running && !hasVpnTransport

    /**
     * Xray does not recover outbound sockets after cell↔Wi-Fi. Restart the live
     * session only when the *underlying* (non-VPN) network identity changed and
     * the new uplink has been stable long enough for a slow radio to finish
     * associating. [previousHandle] null = first observation after connect.
     */
    fun shouldRestartOnUnderlyingSwitch(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        paused: Boolean,
        previousHandle: Long?,
        currentHandle: Long?,
        connectInFlight: Boolean = false,
        settledForMs: Long = UNDERLYING_SWITCH_SETTLE_MS,
        settleWindowMs: Long = UNDERLYING_SWITCH_SETTLE_MS,
    ): Boolean {
        if (!desiredConnection || stopping || paused || connectInFlight) return false
        if (!running || !networkAvailable) return false
        if (previousHandle == null || currentHandle == null) return false
        if (previousHandle == currentHandle) return false
        return settledForMs >= settleWindowMs
    }

    fun shouldApplyRestartDebounce(nowMs: Long, lastRestartAtMs: Long, windowMs: Long): Boolean =
        lastRestartAtMs <= 0L || nowMs - lastRestartAtMs >= windowMs

    fun remainingSettleMs(settledForMs: Long, settleWindowMs: Long = UNDERLYING_SWITCH_SETTLE_MS): Long =
        (settleWindowMs - settledForMs).coerceAtLeast(0L)

    /**
     * Switch decisions only run on ConnectivityManager callbacks. If the uplink
     * stays quiet after the first validated Wi-Fi event, schedule a one-shot
     * recheck when [remainingSettleMs] elapses.
     */
    fun shouldScheduleSettleRecheck(
        previousHandle: Long?,
        currentHandle: Long?,
        connectInFlight: Boolean,
        remainingSettleMs: Long,
    ): Boolean {
        if (connectInFlight || previousHandle == null || currentHandle == null) return false
        if (previousHandle == currentHandle) return false
        return remainingSettleMs > 0L
    }

    /**
     * Remember a handle only when we observed it for the first time, it did not
     * change, or we already restarted onto it. Keep the previous id while a
     * switch is pending (offline handshake / settle) so the restart is not lost.
     */
    fun shouldCommitUnderlyingHandle(
        previousHandle: Long?,
        currentHandle: Long?,
        didRestart: Boolean,
    ): Boolean {
        if (currentHandle == null) return false
        if (didRestart) return true
        if (previousHandle == null) return true
        return previousHandle == currentHandle
    }

    data class NetworkSnapshot(
        val handle: Long,
        val hasVpnTransport: Boolean,
        val hasInternet: Boolean,
        val validated: Boolean,
    )

    /**
     * Prefer a validated non-VPN default; never treat the VPN iface as the switch key.
     * [requireValidated] is used for switch identity so we do not tear down during
     * a Wi-Fi handshake.
     */
    fun pickUnderlyingHandle(
        networks: List<NetworkSnapshot>,
        requireValidated: Boolean = false,
    ): Long? {
        val under = networks.filter { !it.hasVpnTransport }
        val validated = under.firstOrNull { it.hasInternet && it.validated }?.handle
        if (requireValidated) return validated
        return validated ?: under.firstOrNull { it.hasInternet }?.handle
    }

    /** Tracks the candidate uplink until it stops flapping on a slow handoff. */
    data class UnderlyingSwitchWatch(
        val pendingHandle: Long? = null,
        val pendingSinceMs: Long = 0L,
    ) {
        fun observe(currentHandle: Long?, nowMs: Long): UnderlyingSwitchWatch {
            if (currentHandle == null) return UnderlyingSwitchWatch()
            if (currentHandle == pendingHandle) return this
            return UnderlyingSwitchWatch(pendingHandle = currentHandle, pendingSinceMs = nowMs)
        }

        fun settledForMs(currentHandle: Long?, nowMs: Long): Long {
            if (pendingHandle == null || currentHandle == null || pendingHandle != currentHandle) {
                return 0L
            }
            return (nowMs - pendingSinceMs).coerceAtLeast(0L)
        }
    }
}
