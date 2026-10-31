package com.imkolganov.datagate.vpn

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class VpnSessionPhaseUiCycleTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Application>().resources

    @Test
    fun mapperFlags_matchCatalogUiFlags_fromIdle() {
        val idle = VpnStatusUiState()
        for (phase in VpnSessionPhase.catalog) {
            val mapped = VpnEventMapper.map(resources, idle, phase.eventName, "")
            val expected = VpnSessionCyclePolicy.uiFlags(phase)
            assertEquals(phase.name, expected.connectRequested, mapped.isConnectRequested)
            assertEquals(phase.name, expected.connected, mapped.isVpnConnected)
            assertEquals(phase.name, expected.paused, mapped.isVpnPaused)
        }
    }

    @Test
    fun mapper_openVpnUiCycle_matchesPolicyWalk() {
        var ui = VpnStatusUiState(selectedServerName = "Frankfurt", selectedServerId = 4)
        var cycle = VpnSessionCyclePolicy.markActiveEngine(
            VpnSessionCyclePolicy.CycleState(),
            OpenVpn3Service.ENGINE_OPENVPN,
        )
        val events = listOf(
            "CONNECTING",
            "CONNECTED",
            "PAUSED",
            "RESUMED",
            "CONNECTED",
            "DISCONNECTING",
            "DISCONNECTED",
        )
        for (event in events) {
            cycle = VpnSessionCyclePolicy.applyBroadcast(cycle, event, OpenVpn3Service.ENGINE_OPENVPN)
            ui = VpnEventMapper.map(resources, ui, event, "")
            assertEquals(event, cycle.ui.connectRequested, ui.isConnectRequested)
            assertEquals(event, cycle.ui.connected, ui.isVpnConnected)
            assertEquals(event, cycle.ui.paused, ui.isVpnPaused)
        }
        assertFalse(ui.isConnectRequested)
        assertEquals(null, ui.selectedServerName)
    }

    @Test
    fun mapper_xrayUiCycle_clearsOnErrorThenReconnects() {
        var ui = VpnStatusUiState(selectedServerName = "Tokyo")
        for (event in listOf("CONNECTING", "CONNECTED", "ERROR", "CONNECTING", "CONNECTED")) {
            ui = VpnEventMapper.map(resources, ui, event, "boom")
        }
        assertTrue(ui.isVpnConnected)
        assertTrue(ui.lastMessage.contains("Tokyo"))
    }

    @Test
    fun restoreAfterProcessDeath_thenLiveQuery_replaysConnected() {
        val restored = VpnLifecyclePolicy.restoreUiStateOnAppStart(
            current = VpnStatusUiState(),
            cached = VpnLifecyclePolicy.CachedPrefsSnapshot(
                selectedServerName = "Oslo",
                sessionServerId = 2,
                lastEventName = "CONNECTED",
            ),
        ) { state, name, info -> VpnEventMapper.map(resources, state, name, info) }
        assertFalse(restored.isVpnConnected)
        assertEquals("Oslo", restored.selectedServerName)

        val live = VpnLifecyclePolicy.foldStatusBroadcasts(
            restored,
            listOf(VpnLifecyclePolicy.StatusBroadcast("CONNECTED", fromQuery = true)),
        ) { state, name, info -> VpnEventMapper.map(resources, state, name, info) }
        assertTrue(live.isVpnConnected)
    }
}
