package com.imkolganov.datagate.logger

/**
 * Where an OpenVPN [log] line goes besides logcat.
 * WARN/ERROR still use [VpnDebugLogger.w] (file + journal if each sink is on).
 * Other core lines go to the in-memory journal only.
 */
internal enum class EngineJournalCoreLogSink {
    DebugLoggerWarn,
    JournalDebug,
    None,
}

internal object EngineJournalCoreLogPolicy {
    fun sink(persistToDebugFile: Boolean, journalEnabled: Boolean): EngineJournalCoreLogSink =
        when {
            persistToDebugFile -> EngineJournalCoreLogSink.DebugLoggerWarn
            journalEnabled -> EngineJournalCoreLogSink.JournalDebug
            else -> EngineJournalCoreLogSink.None
        }
}
