package com.imkolganov.datagate.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnEventMapperTest {
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
}
