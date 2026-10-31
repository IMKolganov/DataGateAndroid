package com.imkolganov.datagate.vpn.traffic

import com.imkolganov.datagate.vpn.diag.VpnDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-wide 1 Hz sampler. Services [start] a [VpnTrafficSource] while the tunnel is up
 * and [stop] on pause / disconnect. UI observes [uiState]; this is not part of STATUS broadcasts.
 *
 * [owner] is the same idea as [com.imkolganov.datagate.vpn.VpnTunnelSessionStore]: a late
 * peer-engine teardown must not kill the active engine's chart or probes.
 */
object VpnTrafficMonitor {
    const val SAMPLE_INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0
    private var owner: String? = null

    private val _uiState = MutableStateFlow(VpnTrafficUiState())
    val uiState: StateFlow<VpnTrafficUiState> = _uiState.asStateFlow()

    fun start(owner: String? = null, source: VpnTrafficSource) {
        synchronized(lock) {
            job?.cancel()
            val session = ++generation
            this.owner = owner
            VpnDiagnostics.onMonitorStarted()
            _uiState.value = VpnTrafficUiState(isActive = true)
            job = scope.launch {
                val engine = VpnTrafficTickEngine()
                while (isActive) {
                    val nowMs = System.currentTimeMillis()
                    val current = runCatching { source.read() }.getOrNull()
                    val next = engine.tick(current, nowMs)
                    if (session != generation) return@launch
                    _uiState.value = next
                    VpnDiagnostics.onTrafficTick(next, sourceMissing = current == null)
                    delay(SAMPLE_INTERVAL_MS)
                }
            }
        }
    }

    fun stop(expectedOwner: String? = null) {
        synchronized(lock) {
            if (expectedOwner != null && owner != null && owner != expectedOwner) return
            generation++
            owner = null
            job?.cancel()
            job = null
            VpnDiagnostics.cancel()
            _uiState.value = VpnTrafficUiState()
        }
    }

    fun reset() {
        stop(expectedOwner = null)
    }
}
