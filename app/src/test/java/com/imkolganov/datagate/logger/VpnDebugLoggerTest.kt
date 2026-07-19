package com.imkolganov.datagate.logger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * File-session logger contract without Android runtime: exercises rotate + append policy
 * against a temp directory mirroring [VpnDebugLogger]'s layout.
 */
class VpnDebugLoggerTest {

    @Test
    fun rotate_movesCurrentToPrevious_andStartsFreshFile() {
        val dir = createTempDirectory("vpn-debug").toFile()
        try {
            val current = File(dir, VpnDebugLogger.CURRENT_FILE)
            val previous = File(dir, VpnDebugLogger.PREVIOUS_FILE)
            current.writeText("old-session\n")
            assertTrue(current.exists())

            VpnDebugLogRotation.rotate(dir, current)

            assertTrue(previous.exists())
            assertEquals("old-session\n", previous.readText())
            assertTrue(current.exists())
            assertTrue(current.readText().contains("rotated"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun shouldRotate_whenFileAtOrOverLimit() {
        assertTrue(VpnDebugLogRotation.shouldRotate(VpnDebugLogger.MAX_FILE_BYTES))
        assertTrue(VpnDebugLogRotation.shouldRotate(VpnDebugLogger.MAX_FILE_BYTES + 1))
        assertFalse(VpnDebugLogRotation.shouldRotate(VpnDebugLogger.MAX_FILE_BYTES - 1))
        assertFalse(VpnDebugLogRotation.shouldRotate(0))
    }

    @Test
    fun formatLine_includesLevelTagAndThread() {
        val line = VpnDebugLogRotation.formatLine(
            timestampUtc = "2026-07-19T20:00:00.000Z",
            level = "W",
            tag = "OpenVPN3",
            threadName = "test-thread",
            message = "bridge_transport_lost: wss_closed:1006:closed",
            error = null,
        )
        assertEquals(
            "2026-07-19T20:00:00.000Z W/OpenVPN3 [test-thread] bridge_transport_lost: wss_closed:1006:closed\n",
            line,
        )
    }
}
