package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnTransportTest {
    @Test
    fun fromIntentExtra_defaultsToWss() {
        assertEquals(VpnTransport.Wss, VpnTransport.fromIntentExtra(null))
        assertEquals(VpnTransport.Wss, VpnTransport.fromIntentExtra("wss"))
        assertEquals(VpnTransport.Wss, VpnTransport.fromIntentExtra("other"))
    }

    @Test
    fun fromIntentExtra_parsesDirect() {
        assertEquals(VpnTransport.Direct, VpnTransport.fromIntentExtra("direct"))
        assertEquals(VpnTransport.Direct, VpnTransport.fromIntentExtra("DIRECT"))
    }
}
