package com.imkolganov.datagate.logger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineJournalBufferTest {

    @Test
    fun append_ignoredUntilEnabled() {
        val journal = EngineJournalBuffer(maxLines = 10) { "t" }
        assertFalse(journal.append("D", "Tag", "hello"))
        assertEquals(0, journal.size())
        assertTrue(journal.text().isEmpty())
    }

    @Test
    fun append_recordsWhenEnabled() {
        val journal = EngineJournalBuffer(maxLines = 10) { "2026-01-01T00:00:00.000Z" }
        journal.setEnabled(true)
        assertTrue(journal.append("I", "OpenVPN3", "CONNECTED", threadName = "test"))
        assertEquals(1, journal.size())
        assertTrue(journal.text().contains("I/OpenVPN3"))
        assertTrue(journal.text().contains("CONNECTED"))
    }

    @Test
    fun disable_clearsRing() {
        val journal = EngineJournalBuffer(maxLines = 10) { "t" }
        journal.setEnabled(true)
        journal.append("D", "T", "one")
        journal.setEnabled(false)
        assertEquals(0, journal.size())
        assertFalse(journal.isEnabled())
        assertFalse(journal.append("D", "T", "two"))
        assertEquals(0, journal.size())
    }

    @Test
    fun ring_dropsOldestAfterMaxLines() {
        val journal = EngineJournalBuffer(maxLines = 3) { "t" }
        journal.setEnabled(true)
        repeat(5) { i ->
            journal.appendLine("line-$i")
        }
        assertEquals(3, journal.size())
        assertEquals(listOf("line-2", "line-3", "line-4"), journal.snapshot())
    }

    @Test
    fun appendLine_collapsesNewlinesAndSkipsBlank() {
        val journal = EngineJournalBuffer(maxLines = 10) { "t" }
        journal.setEnabled(true)
        assertTrue(journal.appendLine("a\nb"))
        assertFalse(journal.appendLine("   "))
        assertFalse(journal.appendLine("\n"))
        assertEquals(listOf("a b"), journal.snapshot())
    }

    @Test
    fun reenable_startsEmpty() {
        val journal = EngineJournalBuffer(maxLines = 10) { "t" }
        journal.setEnabled(true)
        journal.appendLine("old")
        journal.setEnabled(false)
        journal.setEnabled(true)
        assertEquals(0, journal.size())
        journal.appendLine("new")
        assertEquals(listOf("new"), journal.snapshot())
    }

    @Test
    fun append_usesExactFileLineFormatWithoutTrailingNewline() {
        val journal = EngineJournalBuffer(maxLines = 10) { "2026-01-01T00:00:00.000Z" }
        journal.setEnabled(true)
        journal.append("W", "OpenVPN3", "core: WARN slow", threadName = "ovpn")
        assertEquals(
            listOf("2026-01-01T00:00:00.000Z W/OpenVPN3 [ovpn] core: WARN slow"),
            journal.snapshot(),
        )
    }

    @Test
    fun zeroMaxLines_doesNotGrowOrLoop() {
        val journal = EngineJournalBuffer(maxLines = 0) { "t" }
        journal.setEnabled(true)
        assertFalse(journal.appendLine("x"))
        assertEquals(0, journal.size())
    }

    @Test
    fun thousandLineCap_matchesProductLimit() {
        val journal = EngineJournalBuffer(maxLines = EngineJournal.MAX_LINES) { "t" }
        journal.setEnabled(true)
        repeat(EngineJournal.MAX_LINES + 25) { i ->
            journal.appendLine("row-$i")
        }
        assertEquals(EngineJournal.MAX_LINES, journal.size())
        assertEquals("row-25", journal.snapshot().first())
        assertEquals("row-${EngineJournal.MAX_LINES + 24}", journal.snapshot().last())
    }
}
