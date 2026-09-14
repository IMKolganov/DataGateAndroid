package com.imkolganov.datagate.ui.screens.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineJournalActionsPolicyTest {

    @Test
    fun emptyOrWhitespace_cannotCopyOrClear() {
        assertFalse(EngineJournalActionsPolicy.hasCopyableContent(""))
        assertFalse(EngineJournalActionsPolicy.hasCopyableContent("   \n"))
        assertFalse(EngineJournalActionsPolicy.canClear(""))
    }

    @Test
    fun capturedLines_canCopyAndClear() {
        val text = "2026-01-01 I/OpenVPN3 CONNECTED"
        assertTrue(EngineJournalActionsPolicy.hasCopyableContent(text))
        assertTrue(EngineJournalActionsPolicy.canClear(text))
    }
}
