package com.imkolganov.datagate.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class BridgeTransportLossTest {

    @Test
    fun formatFailureReason_usesMessageWhenPresent() {
        val reason = BridgeTransportLoss.formatFailureReason(IOException("connection reset"))
        assertEquals("wss_failure:connection reset", reason)
    }

    @Test
    fun formatFailureReason_fallsBackToClassName() {
        val reason = BridgeTransportLoss.formatFailureReason(IOException())
        assertEquals("wss_failure:IOException", reason)
    }

    @Test
    fun formatClosedReason_usesReasonWhenPresent() {
        val reason = BridgeTransportLoss.formatClosedReason(1000, "going away")
        assertEquals("wss_closed:1000:going away", reason)
    }

    @Test
    fun formatClosedReason_defaultsWhenBlank() {
        val reason = BridgeTransportLoss.formatClosedReason(1006, "   ")
        assertEquals("wss_closed:1006:closed", reason)
    }

    @Test
    fun notifyOnce_invokesCallbackOnlyOnce() {
        val notified = AtomicBoolean(false)
        var lastReason: String? = null
        val callback: (String) -> Unit = { lastReason = it }

        BridgeTransportLoss.notifyOnce(notified, callback, "first")
        BridgeTransportLoss.notifyOnce(notified, callback, "second")

        assertEquals("first", lastReason)
    }

    @Test
    fun notifyOnce_noOpWhenCallbackNull() {
        val notified = AtomicBoolean(false)
        BridgeTransportLoss.notifyOnce(notified, null, "ignored")
        assertEquals(true, notified.get())
    }

    @Test
    fun formatSendRejectedReason_isStableForLogs() {
        assertEquals("wss_send_rejected", BridgeTransportLoss.formatSendRejectedReason())
    }

    @Test
    fun shouldTreatSendRejectedAsTransportLost_onlyWhenSendReturnsFalse() {
        // TCP↔WSS silent stall: OkHttp queue full / closing socket returns false from send().
        // Ignoring that left OpenVPN CONNECTED with a live local TCP while frames were dropped.
        assertTrue(BridgeTransportLoss.shouldTreatSendRejectedAsTransportLost(sendAccepted = false))
        assertFalse(BridgeTransportLoss.shouldTreatSendRejectedAsTransportLost(sendAccepted = true))
    }
}
