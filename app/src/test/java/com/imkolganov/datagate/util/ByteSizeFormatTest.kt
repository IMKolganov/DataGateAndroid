package com.imkolganov.datagate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatTest {

    @Test
    fun formatBytesPerSecond_appendsSlashS() {
        assertEquals("—", formatBytesPerSecond(null))
        assertEquals("0 B/s", formatBytesPerSecond(0))
        assertEquals("1.0 KB/s", formatBytesPerSecond(1024))
        assertEquals("1.00 MB/s", formatBytesPerSecond(1024L * 1024L))
    }

    @Test
    fun formatBytes_coversSessionTotalsUsedByLiveTrafficCard() {
        assertEquals("—", formatBytes(null))
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("100 KB", formatBytes(100L * 1024L))
        assertEquals("1.00 MB", formatBytes(1024L * 1024L))
        assertEquals("−1.0 KB", formatBytes(-1024))
    }
}
