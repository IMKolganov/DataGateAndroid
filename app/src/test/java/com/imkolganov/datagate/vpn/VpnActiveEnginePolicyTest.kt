package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnActiveEnginePolicyTest {

    @Test
    fun serviceError_clearsActiveEngine() {
        assertTrue(VpnActiveEnginePolicy.shouldClearActiveEngine("ERROR", fromQuery = false))
        assertTrue(VpnActiveEnginePolicy.shouldClearActiveEngine("tun_setup_failed", fromQuery = false))
    }

    @Test
    fun userDisconnected_clearsActiveEngine() {
        assertTrue(VpnActiveEnginePolicy.shouldClearActiveEngine("DISCONNECTED", fromQuery = false))
    }

    @Test
    fun queryAndInFlightEvents_doNotClear() {
        assertFalse(VpnActiveEnginePolicy.shouldClearActiveEngine("DISCONNECTED", fromQuery = true))
        assertFalse(VpnActiveEnginePolicy.shouldClearActiveEngine("ERROR", fromQuery = true))
        assertFalse(VpnActiveEnginePolicy.shouldClearActiveEngine("CONNECTED", fromQuery = false))
        assertFalse(VpnActiveEnginePolicy.shouldClearActiveEngine("CONNECTING", fromQuery = false))
        assertFalse(VpnActiveEnginePolicy.shouldClearActiveEngine("PAUSED", fromQuery = false))
        assertFalse(VpnActiveEnginePolicy.shouldClearActiveEngine("WAITING_NETWORK", fromQuery = false))
    }
}
