package com.imkolganov.datagate.logger

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EngineJournalTest {

    @Before
    @After
    fun reset() {
        EngineJournal.resetForTests()
    }

    @Test
    fun disabled_appendIsIgnored() {
        EngineJournal.append("I", "Tag", "secret")
        EngineJournal.publishNow()
        assertFalse(EngineJournal.isEnabled())
        assertFalse(EngineJournal.enabled.value)
        assertEquals("", EngineJournal.text.value)
    }

    @Test
    fun enabled_publishNow_exposesLine() {
        EngineJournal.setEnabled(true)
        EngineJournal.append("I", "OpenVPN3", "CONNECTED")
        EngineJournal.publishNow()
        assertTrue(EngineJournal.isEnabled())
        assertTrue(EngineJournal.enabled.value)
        assertTrue(EngineJournal.text.value.contains("I/OpenVPN3"))
        assertTrue(EngineJournal.text.value.contains("CONNECTED"))
    }

    @Test
    fun disable_clearsPublishedText() {
        EngineJournal.setEnabled(true)
        EngineJournal.append("D", "T", "old")
        EngineJournal.publishNow()
        EngineJournal.setEnabled(false)
        assertEquals("", EngineJournal.text.value)
        assertFalse(EngineJournal.enabled.value)
    }

    @Test
    fun clear_keepsEnabledAndEmptiesPublishedText() {
        EngineJournal.setEnabled(true)
        EngineJournal.append("I", "T", "old")
        EngineJournal.publishNow()
        EngineJournal.clear()
        assertTrue(EngineJournal.isEnabled())
        assertTrue(EngineJournal.enabled.value)
        assertEquals("", EngineJournal.text.value)
        EngineJournal.append("I", "T", "new")
        EngineJournal.publishNow()
        assertTrue(EngineJournal.text.value.contains("new"))
        assertFalse(EngineJournal.text.value.contains("old"))
    }

    @Test
    fun debounce_publishesAfterDelay() = runBlocking {
        EngineJournal.setEnabled(true)
        EngineJournal.append("I", "T", "debounced")
        assertEquals("", EngineJournal.text.value)
        withTimeout(2_000) {
            while (EngineJournal.text.value.isEmpty()) {
                delay(20)
            }
        }
        assertTrue(EngineJournal.text.value.contains("debounced"))
    }

    @Test
    fun twoAppends_shareOneDebouncedPublish() = runBlocking {
        EngineJournal.setEnabled(true)
        EngineJournal.append("I", "T", "first")
        EngineJournal.append("I", "T", "second")
        withTimeout(2_000) {
            while (!EngineJournal.text.value.contains("second")) {
                delay(20)
            }
        }
        assertTrue(EngineJournal.text.value.contains("first"))
        assertTrue(EngineJournal.text.value.contains("second"))
    }
}
