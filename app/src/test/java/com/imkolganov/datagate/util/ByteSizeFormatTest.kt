package com.imkolganov.datagate.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatTest {

    @Test
    fun formatBytesPerSecond_appendsSlashS() {
        assertEquals("—", formatBytesPerSecond(null))
        assertEquals("0 B/s", formatBytesPerSecond(0))
        assertEquals("${formatBytes(1024)}/s", formatBytesPerSecond(1024))
        assertEquals("${formatBytes(1024L * 1024L)}/s", formatBytesPerSecond(1024L * 1024L))
    }
}
