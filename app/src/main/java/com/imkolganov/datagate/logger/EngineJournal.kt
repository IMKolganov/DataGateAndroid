package com.imkolganov.datagate.logger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/**
 * In-memory ring of the last [MAX_LINES] engine lines for the Home journal.
 * Capture and UI are gated by Settings → Development → show engine journal.
 */
class EngineJournalBuffer(
    private val maxLines: Int = EngineJournal.MAX_LINES,
    private val timestamp: () -> String = { VpnDebugLogRotation.isoUtc(Date()) },
) {
    private val lock = Any()
    private val lines = ArrayDeque<String>(maxLines + 1)
    private var enabled = false

    fun isEnabled(): Boolean = synchronized(lock) { enabled }

    fun size(): Int = synchronized(lock) { lines.size }

    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            enabled = value
            if (!value) lines.clear()
        }
    }

    fun append(
        level: String,
        tag: String,
        message: String,
        threadName: String = Thread.currentThread().name,
    ): Boolean {
        if (!isEnabled()) return false
        val line = VpnDebugLogRotation.formatLine(
            timestampUtc = timestamp(),
            level = level,
            tag = tag,
            threadName = threadName,
            message = message,
            error = null,
        ).trimEnd()
        return appendLine(line)
    }

    fun appendLine(line: String): Boolean {
        synchronized(lock) {
            if (!enabled) return false
            val cleaned = line.replace('\n', ' ').trimEnd()
            if (cleaned.isEmpty() || maxLines <= 0) return false
            while (lines.size >= maxLines) {
                lines.removeFirst()
            }
            lines.addLast(cleaned)
            return true
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { lines.toList() }

    fun text(): String = snapshot().joinToString("\n")
}

object EngineJournal {
    const val MAX_LINES = 1_000
    const val UI_PUBLISH_DEBOUNCE_MS = 250L

    private val buffer = EngineJournalBuffer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val publishLock = Any()
    private var publishJob: Job? = null

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun isEnabled(): Boolean = buffer.isEnabled()

    fun setEnabled(value: Boolean) {
        buffer.setEnabled(value)
        _enabled.value = value
        publishNow()
    }

    fun append(level: String, tag: String, message: String) {
        if (buffer.append(level, tag, message)) {
            schedulePublish()
        }
    }

    fun publishNow() {
        synchronized(publishLock) {
            publishJob?.cancel()
            publishJob = null
        }
        _text.value = buffer.text()
    }

    /** Test hook: drop in-memory state without touching preferences. */
    fun resetForTests() {
        setEnabled(false)
    }

    private fun schedulePublish() {
        synchronized(publishLock) {
            if (publishJob?.isActive == true) return
            publishJob = scope.launch {
                delay(UI_PUBLISH_DEBOUNCE_MS)
                _text.value = buffer.text()
            }
        }
    }
}
