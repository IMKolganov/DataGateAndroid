package com.imkolganov.datagate.vpn.traffic

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTrafficMonitorTest {

    @After
    fun tearDown() {
        VpnTrafficMonitor.stop()
    }

    @Test
    fun start_marksUiActive_stopClearsIt() = runBlocking {
        VpnTrafficMonitor.start { TrafficCounters(100, 20) }
        val active = withTimeout(3_000) {
            while (!VpnTrafficMonitor.uiState.value.isActive) {
                delay(20)
            }
            VpnTrafficMonitor.uiState.value
        }
        assertTrue(active.isActive)

        VpnTrafficMonitor.stop()
        delay(50)
        assertFalse(VpnTrafficMonitor.uiState.value.isActive)
        assertTrue(VpnTrafficMonitor.uiState.value.samples.isEmpty())
    }

    @Test
    fun stop_isIdempotent() {
        VpnTrafficMonitor.stop()
        VpnTrafficMonitor.stop()
        assertFalse(VpnTrafficMonitor.uiState.value.isActive)
    }
}
