package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class VpnLifecycleRobolectricIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun map(state: VpnStatusUiState, name: String, info: String = ""): VpnStatusUiState =
        VpnEventMapper.map(context.resources, state, name, info)

    @Test
    fun appReopen_withRealMapper_pausedCacheBecomesDisconnected() {
        val restored = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = VpnStatusUiState(),
            cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
                selectedServerName = "Frankfurt",
                sessionServerId = 2,
                lastEventName = "PAUSED",
            ),
            mapEvent = ::map,
        )
        assertFalse(restored.isVpnPaused)
        assertFalse(restored.isConnectRequested)
        assertEquals("Frankfurt", restored.selectedServerName)
        assertEquals(2, restored.selectedServerId)
    }

    @Test
    fun disconnect_clearsServerNameWithRealMapper() {
        val connected = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            selectedServerName = "Paris",
            selectedServerId = 4,
        )
        val disconnected = map(connected, "DISCONNECTED")
        assertNull(disconnected.selectedServerName)
        assertNull(disconnected.selectedServerId)
    }

    @Test
    fun fullLifecycle_withRealMapper_stringsAreLocalized() {
        var state = VpnStatusUiState(selectedServerName = "Berlin")
        state = map(state, "CONNECTING")
        assertFalse(state.isVpnConnected)
        state = map(state, "CONNECTED")
        assertTrue(state.isVpnConnected)
        state = map(state, "PAUSED")
        assertTrue(state.isVpnPaused)
        state = map(state, "RESUMED")
        assertFalse(state.isVpnPaused)
        state = map(state, "DISCONNECTED")
        assertFalse(state.isConnectRequested)
        assertFalse(state.lastMessage.isBlank())
    }
}
