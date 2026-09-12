package com.imkolganov.datagate.logger

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineJournalCoreLogPolicyTest {

    @Test
    fun warnLine_alwaysGoesThroughDebugLogger_evenIfJournalOff() {
        assertEquals(
            EngineJournalCoreLogSink.DebugLoggerWarn,
            EngineJournalCoreLogPolicy.sink(persistToDebugFile = true, journalEnabled = false),
        )
        assertEquals(
            EngineJournalCoreLogSink.DebugLoggerWarn,
            EngineJournalCoreLogPolicy.sink(persistToDebugFile = true, journalEnabled = true),
        )
    }

    @Test
    fun infoLine_goesToJournalOnlyWhenEnabled() {
        assertEquals(
            EngineJournalCoreLogSink.JournalDebug,
            EngineJournalCoreLogPolicy.sink(persistToDebugFile = false, journalEnabled = true),
        )
        assertEquals(
            EngineJournalCoreLogSink.None,
            EngineJournalCoreLogPolicy.sink(persistToDebugFile = false, journalEnabled = false),
        )
    }
}
