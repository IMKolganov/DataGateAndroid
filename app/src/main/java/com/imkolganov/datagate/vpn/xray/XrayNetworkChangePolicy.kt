package com.imkolganov.datagate.vpn.xray

/** Outcome of one network callback for a live or recovering Xray session. */
internal enum class XrayNetworkFollowUp {
    RESTART_SWITCH,
    RECONNECT,
    WAIT,
    HEALTH,
}

internal data class XrayNetworkChangeState(
    val lastHandle: Long? = null,
    val lastRestartAtMs: Long = 0L,
    val watch: XrayNetworkPolicy.UnderlyingSwitchWatch = XrayNetworkPolicy.UnderlyingSwitchWatch(),
)

internal data class XrayNetworkChangeDecision(
    val followUp: XrayNetworkFollowUp,
    val state: XrayNetworkChangeState,
    val scheduleSettleRecheck: Boolean,
    val cancelSettleRecheck: Boolean,
    val remainingWaitMs: Long,
    val settledForMs: Long,
)

/** Orchestrates switch / reconnect / settle for [XrayVpnService.onNetworkStateChanged]. */
internal object XrayNetworkChangePolicy {
    fun remainingDebounceMs(
        nowMs: Long,
        lastRestartAtMs: Long,
        windowMs: Long = XrayNetworkPolicy.UNDERLYING_SWITCH_DEBOUNCE_MS,
    ): Long {
        if (lastRestartAtMs <= 0L) return 0L
        return (windowMs - (nowMs - lastRestartAtMs)).coerceAtLeast(0L)
    }

    fun networkIdentityHandle(sdkInt: Int, api28Handle: Long, hashCode: Int): Long =
        if (sdkInt >= 28) api28Handle else hashCode.toLong()

    fun resolve(
        desiredConnection: Boolean,
        stopping: Boolean,
        running: Boolean,
        networkAvailable: Boolean,
        paused: Boolean,
        connectInFlight: Boolean,
        switchHandle: Long?,
        nowMs: Long,
        previous: XrayNetworkChangeState,
        settleWindowMs: Long = XrayNetworkPolicy.UNDERLYING_SWITCH_SETTLE_MS,
        debounceWindowMs: Long = XrayNetworkPolicy.UNDERLYING_SWITCH_DEBOUNCE_MS,
    ): XrayNetworkChangeDecision {
        val watch = previous.watch.observe(switchHandle, nowMs)
        val settledForMs = watch.settledForMs(switchHandle, nowMs)
        val remainingSettleMs = XrayNetworkPolicy.remainingSettleMs(settledForMs, settleWindowMs)
        val remainingWaitMs = maxOf(
            remainingSettleMs,
            remainingDebounceMs(nowMs, previous.lastRestartAtMs, debounceWindowMs),
        )
        val switched = XrayNetworkPolicy.shouldRestartOnUnderlyingSwitch(
            desiredConnection = desiredConnection,
            stopping = stopping,
            running = running,
            networkAvailable = networkAvailable,
            paused = paused,
            previousHandle = previous.lastHandle,
            currentHandle = switchHandle,
            connectInFlight = connectInFlight,
            settledForMs = settledForMs,
            settleWindowMs = settleWindowMs,
        ) && XrayNetworkPolicy.shouldApplyRestartDebounce(
            nowMs = nowMs,
            lastRestartAtMs = previous.lastRestartAtMs,
            windowMs = debounceWindowMs,
        )
        val nextLastHandle = if (
            XrayNetworkPolicy.shouldCommitUnderlyingHandle(
                previousHandle = previous.lastHandle,
                currentHandle = switchHandle,
                didRestart = switched,
            )
        ) {
            switchHandle
        } else {
            previous.lastHandle
        }
        val followUp = when {
            switched -> XrayNetworkFollowUp.RESTART_SWITCH
            XrayNetworkPolicy.shouldReconnect(
                desiredConnection = desiredConnection,
                stopping = stopping,
                running = running,
                networkAvailable = networkAvailable,
                paused = paused,
                connectInFlight = connectInFlight,
            ) -> XrayNetworkFollowUp.RECONNECT
            XrayNetworkPolicy.shouldWaitForNetwork(
                desiredConnection = desiredConnection,
                stopping = stopping,
                running = running,
                networkAvailable = networkAvailable,
                paused = paused,
                connectInFlight = connectInFlight,
            ) -> XrayNetworkFollowUp.WAIT
            else -> XrayNetworkFollowUp.HEALTH
        }
        val scheduleSettleRecheck = followUp == XrayNetworkFollowUp.HEALTH &&
            XrayNetworkPolicy.shouldScheduleSettleRecheck(
                previousHandle = nextLastHandle,
                currentHandle = switchHandle,
                connectInFlight = connectInFlight,
                remainingSettleMs = remainingWaitMs,
            )
        val cancelSettleRecheck = !scheduleSettleRecheck && (
            followUp != XrayNetworkFollowUp.HEALTH ||
                switchHandle == null ||
                switchHandle == nextLastHandle
            )
        return XrayNetworkChangeDecision(
            followUp = followUp,
            state = XrayNetworkChangeState(
                lastHandle = nextLastHandle,
                lastRestartAtMs = if (switched) nowMs else previous.lastRestartAtMs,
                watch = watch,
            ),
            scheduleSettleRecheck = scheduleSettleRecheck,
            cancelSettleRecheck = cancelSettleRecheck,
            remainingWaitMs = remainingWaitMs,
            settledForMs = settledForMs,
        )
    }
}
