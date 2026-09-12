package com.imkolganov.datagate.vpn.traffic

data class TrafficDeltaResult(
    val previous: TrafficCounters?,
    val previousAtMs: Long,
    val sessionBytesIn: Long,
    val sessionBytesOut: Long,
    val samples: List<TrafficSample>,
    val speedInBps: Long,
    val speedOutBps: Long,
) {
    fun toUiState(isActive: Boolean): VpnTrafficUiState = VpnTrafficUiState(
        isActive = isActive,
        speedInBps = speedInBps,
        speedOutBps = speedOutBps,
        sessionBytesIn = sessionBytesIn,
        sessionBytesOut = sessionBytesOut,
        samples = samples,
    )
}

/**
 * Mirrors DataGateMac [pollLiveTraffic]: first snapshot is baseline only,
 * session totals are the latest interface counters, and a backward jump
 * is a new baseline (no negative speed, no extra zero sample).
 */
object TrafficDelta {
    const val MAX_SAMPLES = 60
    const val MIN_CHART_SAMPLES = 2

    fun apply(
        previous: TrafficCounters?,
        previousAtMs: Long,
        current: TrafficCounters?,
        nowMs: Long,
        sessionBytesIn: Long,
        sessionBytesOut: Long,
        samples: List<TrafficSample>,
        lastSpeedInBps: Long = 0,
        lastSpeedOutBps: Long = 0,
        maxSamples: Int = MAX_SAMPLES,
    ): TrafficDeltaResult {
        if (current == null) {
            return TrafficDeltaResult(
                previous = previous,
                previousAtMs = previousAtMs,
                sessionBytesIn = sessionBytesIn,
                sessionBytesOut = sessionBytesOut,
                samples = samples,
                speedInBps = lastSpeedInBps,
                speedOutBps = lastSpeedOutBps,
            )
        }

        if (previous == null ||
            current.bytesIn < previous.bytesIn ||
            current.bytesOut < previous.bytesOut
        ) {
            return TrafficDeltaResult(
                previous = current,
                previousAtMs = nowMs,
                sessionBytesIn = current.bytesIn,
                sessionBytesOut = current.bytesOut,
                samples = samples,
                speedInBps = 0,
                speedOutBps = 0,
            )
        }

        val dtMs = nowMs - previousAtMs
        if (dtMs <= 0L) {
            return TrafficDeltaResult(
                previous = current,
                previousAtMs = previousAtMs,
                sessionBytesIn = current.bytesIn,
                sessionBytesOut = current.bytesOut,
                samples = samples,
                speedInBps = lastSpeedInBps,
                speedOutBps = lastSpeedOutBps,
            )
        }

        val speedIn = (current.bytesIn - previous.bytesIn) * 1000L / dtMs
        val speedOut = (current.bytesOut - previous.bytesOut) * 1000L / dtMs
        return TrafficDeltaResult(
            previous = current,
            previousAtMs = nowMs,
            sessionBytesIn = current.bytesIn,
            sessionBytesOut = current.bytesOut,
            samples = append(samples, TrafficSample(speedIn, speedOut), maxSamples),
            speedInBps = speedIn,
            speedOutBps = speedOut,
        )
    }

    fun append(
        samples: List<TrafficSample>,
        sample: TrafficSample,
        maxSamples: Int = MAX_SAMPLES,
    ): List<TrafficSample> {
        if (maxSamples <= 0) return emptyList()
        if (samples.size < maxSamples) return samples + sample
        if (samples.size == maxSamples) {
            return samples.subList(1, samples.size) + sample
        }
        return samples.takeLast(maxSamples - 1) + sample
    }
}
