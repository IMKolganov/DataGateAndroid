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

    @Test(timeout = 5_000)
    fun writer_storm_completesQuickly_withoutHang() {
        val dir = createTempDirectory("vpn-debug-storm").toFile()
        val writer = VpnDebugLogWriter(dir = dir, queueCapacity = 256)
        try {
            repeat(1_000) { i ->
                writer.enqueue("line-$i\n")
            }
            assertTrue(writer.flush(timeoutMs = 3_000))
            val file = File(dir, VpnDebugLogger.CURRENT_FILE)
            assertTrue(file.isFile)
            assertTrue(file.length() > 0L)
            assertTrue(
                "Expected some lines persisted under storm",
                file.readText().contains("line-"),
            )
        } finally {
            writer.shutdown()
            dir.deleteRecursively()
        }
    }

    @Test(timeout = 5_000)
    fun writer_tinyQueue_dropsNewestUnderStorm() {
        val dir = createTempDirectory("vpn-debug-drop").toFile()
        val writer = VpnDebugLogWriter(dir = dir, queueCapacity = 2)
        try {
            // Overwhelm before the writer thread drains.
            repeat(200) { i ->
                writer.enqueue("storm-$i\n")
            }
            assertTrue(
                "Tiny queue must drop newest under storm",
                writer.droppedCount() > 0L,
            )
            assertTrue(writer.flush(timeoutMs = 3_000))
            val body = File(dir, VpnDebugLogger.CURRENT_FILE).readText()
            assertTrue(body.contains("storm-"))
        } finally {
            writer.shutdown()
            dir.deleteRecursively()
        }
    }
}
