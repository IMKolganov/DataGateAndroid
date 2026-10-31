package com.imkolganov.datagate.ui.screens.connect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineJournalScrollPolicyTest {

    @Test
    fun emptyOrUnfilled_staysStuck() {
        assertTrue(EngineJournalScrollPolicy.shouldStickToBottom(scrollValue = 0, maxValue = 0))
        assertTrue(EngineJournalScrollPolicy.shouldStickToBottom(scrollValue = 0, maxValue = -1))
    }

    @Test
    fun nearBottom_staysStuck() {
        assertTrue(
            EngineJournalScrollPolicy.shouldStickToBottom(
                scrollValue = 1_000 - EngineJournalScrollPolicy.STICK_THRESHOLD_PX,
                maxValue = 1_000,
            ),
        )
        assertTrue(
            EngineJournalScrollPolicy.shouldStickToBottom(scrollValue = 999, maxValue = 1_000),
        )
    }

    @Test
    fun scrolledUp_releasesStick() {
        assertFalse(
            EngineJournalScrollPolicy.shouldStickToBottom(
                scrollValue = 1_000 - EngineJournalScrollPolicy.STICK_THRESHOLD_PX - 1,
                maxValue = 1_000,
            ),
        )
        assertFalse(EngineJournalScrollPolicy.shouldStickToBottom(scrollValue = 0, maxValue = 400))
    }
}
