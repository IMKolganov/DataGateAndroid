package com.imkolganov.datagate.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.imkolganov.datagate.R
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
class VpnEventMapperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun shouldShowReconnectingOnNetworkChange_returnsFalseWhenConnected() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            lastMessage = "Connected"
        )
        assertFalse(VpnEventMapper.shouldShowReconnectingOnNetworkChange(previous))
    }

    @Test
    fun shouldShowReconnectingOnNetworkChange_returnsTrueWhenNotConnected() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = false,
            lastMessage = "Connecting"
        )
        assertTrue(VpnEventMapper.shouldShowReconnectingOnNetworkChange(previous))
    }

    @Test
    fun map_paused_clearsConnectedAndSetsPauseFlag() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            isVpnPaused = false
        )
        val next = VpnEventMapper.map(context.resources, previous, "PAUSED", "")

        assertTrue(next.isConnectRequested)
        assertFalse(next.isVpnConnected)
        assertTrue(next.isVpnPaused)
    }

    @Test
    fun map_resumed_clearsPauseFlag() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = false,
            isVpnPaused = true
        )
        val next = VpnEventMapper.map(context.resources, previous, "RESUMED", "")

        assertTrue(next.isConnectRequested)
        assertFalse(next.isVpnConnected)
        assertFalse(next.isVpnPaused)
    }

    @Test
    fun map_connected_clearsPauseAndSetsConnected() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnPaused = true,
            selectedServerName = "Frankfurt"
        )
        val next = VpnEventMapper.map(context.resources, previous, "CONNECTED", "")

        assertTrue(next.isVpnConnected)
        assertFalse(next.isVpnPaused)
        assertTrue(next.lastMessage.contains("Frankfurt"))
    }

    @Test
    fun map_waitingNetwork_keepsConnectRequestedAndClearsConnected() {
        val previous = VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        val next = VpnEventMapper.map(context.resources, previous, "WAITING_NETWORK", "")

        assertTrue(next.isConnectRequested)
        assertFalse(next.isVpnConnected)
    }

    @Test
    fun map_reconnectingAndConnecting_clearConnectedFlag() {
        val connected = VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)

        val reconnecting = VpnEventMapper.map(context.resources, connected, "RECONNECTING", "")
        assertFalse(reconnecting.isVpnConnected)

        val connecting = VpnEventMapper.map(context.resources, connected, "CONNECTING", "")
        assertFalse(connecting.isVpnConnected)
    }

    @Test
    fun map_reconnecting_countsOncePerCycleUntilConnected() {
        val connected = VpnStatusUiState(isConnectRequested = true, isVpnConnected = true)
        val first = VpnEventMapper.map(context.resources, connected, "RECONNECTING", "")
        assertEquals(1, first.reconnectCount)
        assertTrue(first.reconnectCycleActive)

        val spam = VpnEventMapper.map(context.resources, first, "RECONNECTING", "")
        assertEquals(1, spam.reconnectCount)

        val back = VpnEventMapper.map(context.resources, spam, "CONNECTED", "")
        assertEquals(1, back.reconnectCount)
        assertFalse(back.reconnectCycleActive)

        val second = VpnEventMapper.map(context.resources, back, "RECONNECTING", "")
        assertEquals(2, second.reconnectCount)

        val gone = VpnEventMapper.map(context.resources, second, "DISCONNECTED", "")
        assertEquals(0, gone.reconnectCount)
    }

    @Test
    fun map_connecting_doesNotCountAsReconnect() {
        val idle = VpnStatusUiState()
        val connecting = VpnEventMapper.map(context.resources, idle, "CONNECTING", "")
        assertEquals(0, connecting.reconnectCount)
        val connected = VpnEventMapper.map(context.resources, connecting, "CONNECTED", "")
        assertEquals(0, connected.reconnectCount)
        assertEquals(
            1,
            VpnReconnectCountPolicy.estimatedSessions(
                connected.reconnectCount,
                sessionActive = connected.isConnectRequested,
            ),
        )
    }

    @Test
    fun map_paused_endsReconnectCycleKeepsCount() {
        val mid = VpnStatusUiState(
            isConnectRequested = true,
            reconnectCount = 3,
            reconnectCycleActive = true,
        )
        val paused = VpnEventMapper.map(context.resources, mid, "PAUSED", "")
        assertEquals(3, paused.reconnectCount)
        assertFalse(paused.reconnectCycleActive)
    }

    @Test
    fun map_error_resetsReconnectCount() {
        val mid = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            reconnectCount = 5,
            reconnectCycleActive = true,
        )
        val error = VpnEventMapper.map(context.resources, mid, "ERROR", "boom")
        assertEquals(0, error.reconnectCount)
        assertFalse(error.reconnectCycleActive)
    }

    @Test
    fun map_disconnecting_clearsSessionFlags() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            isVpnPaused = true
        )
        val next = VpnEventMapper.map(context.resources, previous, "DISCONNECTING", "")

        assertFalse(next.isConnectRequested)
        assertFalse(next.isVpnConnected)
        assertFalse(next.isVpnPaused)
    }

    @Test
    fun map_disconnected_whileConnecting_clearsConnectBusyForNotificationCancel() {
        // Notification Disconnect mid-connect must not leave connectBusy stuck.
        // Peer OpenVPN↔Xray teardown is ignored by VpnController via VpnEngineStatusPolicy.
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = false,
            selectedServerId = 42,
            selectedServerName = "Frankfurt",
        )
        val next = VpnEventMapper.map(context.resources, previous, "DISCONNECTED", "")

        assertFalse(next.isConnectRequested)
        assertFalse(next.isVpnConnected)
        assertNull(next.selectedServerId)
        assertNull(next.selectedServerName)
    }

    @Test
    fun map_disconnected_whileConnected_clearsConnectBusy() {
        // Notification Disconnect does not go through VpnController.requestDisconnect().
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            selectedServerId = 7,
            selectedServerName = "Berlin",
        )
        val next = VpnEventMapper.map(context.resources, previous, "DISCONNECTED", "")

        assertFalse(next.isConnectRequested)
        assertFalse(next.isVpnConnected)
        assertNull(next.selectedServerId)
        assertNull(next.selectedServerName)
    }

    @Test
    fun map_engineSwitch_peerDisconnectedThenConnected_keepsServerNameInStatus() {
        // Mapper clears on peer DISCONNECTED; VpnController ignores inactive-engine events so
        // a live Xray session is not wiped — simulate ignore by keeping selection.
        var state = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = false,
            selectedServerId = 88,
            selectedServerName = "DataGate+🇳🇴+Norway+xray",
        )
        state = VpnEventMapper.map(context.resources, state, "DISCONNECTED", "peer teardown")
        assertNull(state.selectedServerName)
        assertFalse(state.isConnectRequested)
        state = state.copy(
            isConnectRequested = true,
            selectedServerId = 88,
            selectedServerName = "DataGate+🇳🇴+Norway+xray",
        )

        state = VpnEventMapper.map(context.resources, state, "CONNECTED", "")
        assertTrue(state.isVpnConnected)
        assertTrue(state.lastMessage.contains("DataGate"))
        assertTrue(state.lastMessage.contains("Norway"))
        assertEquals(88, state.selectedServerId)
    }

    @Test
    fun map_disconnected_afterUserDisconnect_clearsSelectedServerIdAndName() {
        val previous = VpnStatusUiState(
            isConnectRequested = false,
            isVpnConnected = false,
            selectedServerId = 42,
            selectedServerName = "Frankfurt",
        )
        val next = VpnEventMapper.map(context.resources, previous, "DISCONNECTED", "")

        assertNull(next.selectedServerId)
        assertNull(next.selectedServerName)
        assertFalse(next.isConnectRequested)
        assertFalse(next.isVpnPaused)
    }

    @Test
    fun map_networkChanged_isStableWhileConnected() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = true,
            lastMessage = "Connected"
        )
        val next = VpnEventMapper.map(context.resources, previous, "NETWORK_CHANGED", "")
        assertEquals(previous, next)
    }

    @Test
    fun sanitizeFallbackMessage_hidesNoisyNetworkDetails() {
        val sanitized = VpnEventMapper.sanitizeFallbackMessage(
            eventName = "CUSTOM",
            eventInfo = "192.168.0.1:54321 extra noise that should be hidden"
        )
        assertEquals("CUSTOM", sanitized)
    }

    @Test
    fun map_unknownPlaceholder_showsDisconnectedWhenIdle() {
        val previous = VpnStatusUiState(lastMessage = "")
        val next = VpnEventMapper.map(context.resources, previous, "UNKNOWN", "No status yet")
        assertEquals(
            context.getString(R.string.vpn_msg_disconnected),
            next.lastMessage,
        )
    }

    @Test
    fun map_unknownPlaceholder_doesNotOverwriteConnecting() {
        val previous = VpnStatusUiState(
            isConnectRequested = true,
            lastMessage = "Connecting…",
        )
        val next = VpnEventMapper.map(context.resources, previous, "UNKNOWN", "No status yet")
        assertEquals(previous, next)
    }

    @Test
    fun map_ignoresProgressEventsWhilePaused() {
        val paused = VpnStatusUiState(
            isConnectRequested = true,
            isVpnConnected = false,
            isVpnPaused = true,
            lastMessage = "Paused"
        )

        val afterWait = VpnEventMapper.map(context.resources, paused, "WAIT", "")
        assertEquals(paused, afterWait)

        val afterConnecting = VpnEventMapper.map(context.resources, paused, "CONNECTING", "")
        assertEquals(paused, afterConnecting)
    }

    @Test
    fun isAuthoritativeWhilePaused_allowsResumeAndDisconnect() {
        assertTrue(VpnEventMapper.isAuthoritativeWhilePaused("RESUMED"))
        assertTrue(VpnEventMapper.isAuthoritativeWhilePaused("DISCONNECTED"))
        assertFalse(VpnEventMapper.isAuthoritativeWhilePaused("WAIT"))
    }
}
