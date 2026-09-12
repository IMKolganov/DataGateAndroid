package com.imkolganov.datagate.logger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnDebugLogFormatTest {

    @Test
    fun eventMessage_withoutDetails() {
        assertEquals("EVENT vpn.connected", VpnDebugLogFormat.eventMessage("vpn", "connected"))
    }

    @Test
    fun eventMessage_skipsNullAndBlankValues() {
        val message = VpnDebugLogFormat.eventMessage(
            category = "debug",
            action = "enabled",
            details = mapOf(
                "path" to "/tmp/vpn_debug.txt",
                "empty" to "",
                "blank" to "   ",
                "missing" to null,
            ),
        )
        assertEquals("EVENT debug.enabled path=/tmp/vpn_debug.txt", message)
    }

    @Test
    fun eventMessage_sanitizesNewlinesAndQuotes() {
        val message = VpnDebugLogFormat.eventMessage(
            category = "core",
            action = "log",
            details = mapOf("text" to "line1\nline2 \"quoted\""),
        )
        assertEquals("EVENT core.log text=line1 line2 'quoted'", message)
    }

    @Test
    fun eventMessage_clipsLongValues() {
        val raw = "x".repeat(300)
        val message = VpnDebugLogFormat.eventMessage("cat", "act", mapOf("k" to raw))
        assertTrue(message.endsWith("..."))
        assertEquals(237 + 3, message.substringAfter("k=").length)
        assertFalse(message.contains(raw))
    }

    @Test
    fun journalMessage_withoutError_isUnchanged() {
        assertEquals("hello", VpnDebugLogFormat.journalMessage("hello", null))
    }

    @Test
    fun journalMessage_appendsSimpleExceptionName() {
        assertEquals(
            "boom | IllegalStateException: nope",
            VpnDebugLogFormat.journalMessage("boom", IllegalStateException("nope")),
        )
        assertEquals(
            "boom | IllegalStateException: ",
            VpnDebugLogFormat.journalMessage("boom", IllegalStateException()),
        )
    }
}
