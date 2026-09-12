package com.imkolganov.datagate.vpn.diag

import com.imkolganov.datagate.vpn.traffic.VpnTrafficUiState

internal data class TrafficStallTick(
    val emitStall: Boolean = false,
    val emitRecovered: Boolean = false,
    val emitSourceMissing: Boolean = false,
    val zeroSeconds: Int = 0,
)

/** Consecutive 1 Hz zero-speed samples while CONNECTED → silent blackhole vs idle. */
internal class TrafficStallTracker(
    private val stallAfterSec: Int = STALL_AFTER_SEC,
    private val resendEverySec: Int = RESEND_EVERY_SEC,
    private val sourceMissingAfterSec: Int = SOURCE_MISSING_AFTER_SEC,
) {
    private var zeroRun = 0
    private var missingRun = 0
    private var wasStalled = false
    private var lastStallEmitAt = -1
    private var sourceMissingEmitted = false

    fun onTick(state: VpnTrafficUiState, sourceMissing: Boolean): TrafficStallTick {
        if (!state.isActive) {
            reset()
            return TrafficStallTick()
        }

        if (sourceMissing) {
            missingRun++
        } else {
            missingRun = 0
        }
        val emitMissing = !sourceMissingEmitted && missingRun >= sourceMissingAfterSec
        if (emitMissing) sourceMissingEmitted = true

        val last = state.samples.lastOrNull()
        val idleNow = last != null && last.speedInBps == 0L && last.speedOutBps == 0L
        if (idleNow) zeroRun++ else zeroRun = 0

        val stalled = zeroRun >= stallAfterSec
        val emitStall = stalled && (!wasStalled || zeroRun - lastStallEmitAt >= resendEverySec)
        if (emitStall) lastStallEmitAt = zeroRun
        val emitRecovered = wasStalled && !stalled && !idleNow
        wasStalled = stalled
        return TrafficStallTick(
            emitStall = emitStall,
            emitRecovered = emitRecovered,
            emitSourceMissing = emitMissing,
            zeroSeconds = zeroRun,
        )
    }

    fun reset() {
        zeroRun = 0
        missingRun = 0
        wasStalled = false
        lastStallEmitAt = -1
        sourceMissingEmitted = false
    }

    companion object {
        const val STALL_AFTER_SEC = 15
        const val RESEND_EVERY_SEC = 60
        const val SOURCE_MISSING_AFTER_SEC = 5
    }
}
