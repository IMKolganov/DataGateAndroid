package com.imkolganov.datagate.vpn.traffic

/** One connected session of [TrafficDelta] ticks — used by [VpnTrafficMonitor] and unit tests. */
internal class VpnTrafficTickEngine {
    private var previous: TrafficCounters? = null
    private var previousAtMs = 0L
    private var sessionBytesIn = 0L
    private var sessionBytesOut = 0L
    private var samples = emptyList<TrafficSample>()
    private var lastSpeedInBps = 0L
    private var lastSpeedOutBps = 0L

    fun tick(current: TrafficCounters?, nowMs: Long): VpnTrafficUiState {
        val result = TrafficDelta.apply(
            previous = previous,
            previousAtMs = previousAtMs,
            current = current,
            nowMs = nowMs,
            sessionBytesIn = sessionBytesIn,
            sessionBytesOut = sessionBytesOut,
            samples = samples,
            lastSpeedInBps = lastSpeedInBps,
            lastSpeedOutBps = lastSpeedOutBps,
        )
        previous = result.previous
        previousAtMs = result.previousAtMs
        sessionBytesIn = result.sessionBytesIn
        sessionBytesOut = result.sessionBytesOut
        samples = result.samples
        lastSpeedInBps = result.speedInBps
        lastSpeedOutBps = result.speedOutBps
        return result.toUiState(isActive = true)
    }
}
