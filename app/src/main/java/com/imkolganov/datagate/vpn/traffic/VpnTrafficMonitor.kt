package com.imkolganov.datagate.vpn.traffic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide 1 Hz sampler. Services [start] a [VpnTrafficSource] while the tunnel is up
 * and [stop] on pause / disconnect. UI observes [uiState]; this is not part of STATUS broadcasts.
 */
object VpnTrafficMonitor {
    const val SAMPLE_INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0

    private val _uiState = MutableStateFlow(VpnTrafficUiState())
    val uiState: StateFlow<VpnTrafficUiState> = _uiState.asStateFlow()

    fun start(source: VpnTrafficSource) {
        synchronized(lock) {
            job?.cancel()
            val session = ++generation
            _uiState.value = VpnTrafficUiState(isActive = true)
            job = scope.launch {
                var previous: TrafficCounters? = null
                var previousAtMs = 0L
                var sessionBytesIn = 0L
                var sessionBytesOut = 0L
                var samples = emptyList<TrafficSample>()
                while (isActive) {
                    val nowMs = System.currentTimeMillis()
                    val current = runCatching { source.read() }.getOrNull()
                    val tick = TrafficDelta.apply(
                        previous = previous,
                        previousAtMs = previousAtMs,
                        current = current,
                        nowMs = nowMs,
                        sessionBytesIn = sessionBytesIn,
                        sessionBytesOut = sessionBytesOut,
                        samples = samples,
                        lastSpeedInBps = _uiState.value.speedInBps,
                        lastSpeedOutBps = _uiState.value.speedOutBps,
                    )
                    previous = tick.previous
                    previousAtMs = tick.previousAtMs
                    sessionBytesIn = tick.sessionBytesIn
                    sessionBytesOut = tick.sessionBytesOut
                    samples = tick.samples
                    if (session != generation) return@launch
                    _uiState.value = tick.toUiState(isActive = true)
                    delay(SAMPLE_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            job?.cancel()
            job = null
            _uiState.value = VpnTrafficUiState()
        }
    }

    fun reset() {
        stop()
    }
}
