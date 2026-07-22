package com.imkolganov.datagate.logger

import android.content.Context
import android.os.Build
import android.util.Log
import com.imkolganov.datagate.BuildConfig
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session file logger for VPN / network / UI diagnostics.
 *
 * Enable in Settings → **VPN Debug Log**. Writes under
 * [Context.getNoBackupFilesDir]/`debug/vpn_debug.txt` (rotates to `.prev.txt`).
 * Share/preview/clear from the same Settings card. Never auto-uploads.
 */
class VpnDebugLogger(
    context: Context,
    queueCapacity: Int = VpnDebugLogWriter.DEFAULT_QUEUE_CAPACITY,
) {

    private val appContext = context.applicationContext
    private val enabled = AtomicBoolean(false)

    private val dir: File by lazy {
        File(appContext.noBackupFilesDir, DIR_NAME).apply { mkdirs() }
    }

    private val writer: VpnDebugLogWriter by lazy {
        VpnDebugLogWriter(dir = dir, queueCapacity = queueCapacity)
    }

    fun isEnabled(): Boolean = enabled.get()

    fun currentFile(): File = File(dir, CURRENT_FILE)

    fun currentFilePath(): String = currentFile().absolutePath

    fun setEnabled(value: Boolean) {
        val wasEnabled = enabled.getAndSet(value)
        if (value && !wasEnabled) {
            writeSessionHeader()
        }
        event(
            category = "debug",
            action = if (value) "enabled" else "disabled",
            details = mapOf("path" to currentFilePath()),
        )
    }

    /** Structured breadcrumb — preferred for forensics (readable story in the file). */
    fun event(category: String, action: String, details: Map<String, Any?> = emptyMap()) {
        val detailText = details.entries
            .filter { it.value != null && it.value.toString().isNotBlank() }
            .joinToString(" ") { (k, v) ->
                val raw = v.toString().replace('\n', ' ').replace('"', '\'')
                val clipped = if (raw.length > 240) raw.take(237) + "..." else raw
                "$k=$clipped"
            }
        val message = buildString {
            append("EVENT ")
            append(category)
            append('.')
            append(action)
            if (detailText.isNotEmpty()) {
                append(' ')
                append(detailText)
            }
        }
        i(TAG_EVENT, message)
    }

    fun d(tag: String, message: String) = append("D", tag, message, null)

    fun i(tag: String, message: String) = append("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = append("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = append("E", tag, message, error)

    fun logFiles(): List<File> {
        writer.flush()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun totalBytes(): Long = logFiles().sumOf { it.length() }

    /** Last [maxChars] of the active log for in-app preview. */
    fun readTail(maxChars: Int = 12_000): String {
        writer.flush()
        val file = currentFile()
        if (!file.isFile || file.length() == 0L) return ""
        return runCatching {
            val bytes = file.readBytes()
            val start = (bytes.size - maxChars).coerceAtLeast(0)
            String(bytes, start, bytes.size - start, Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun clearLogs() {
        writer.clearAndAwait()
        Log.i(TAG, "debug logs cleared")
        if (enabled.get()) {
            writeSessionHeader()
            event("debug", "cleared_and_restarted", mapOf("path" to currentFilePath()))
        }
    }

    /** Test / diagnostics: lines dropped because the async queue was full. */
    fun droppedCount(): Long = writer.droppedCount()

    private fun writeSessionHeader() {
        val header = buildString {
            appendLine("==== VPN debug session start ${VpnDebugLogRotation.isoUtc(Date())} ====")
            appendLine("file=${currentFilePath()}")
            appendLine("app_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("build_type=${BuildConfig.BUILD_TYPE}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("release=${Build.VERSION.RELEASE}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("abi=${Build.SUPPORTED_ABIS.joinToString(",")}")
            appendLine("note=Look for lines starting with EVENT — they form the session story")
        }
        appendRaw(header)
    }

    private fun append(level: String, tag: String, message: String, error: Throwable?) {
        when (level) {
            "E" -> if (error != null) Log.e(tag, message, error) else Log.e(tag, message)
            "W" -> if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }
        if (!enabled.get()) return

        appendRaw(
            VpnDebugLogRotation.formatLine(
                timestampUtc = VpnDebugLogRotation.isoUtc(Date()),
                level = level,
                tag = tag,
                threadName = Thread.currentThread().name,
                message = message,
                error = error,
            )
        )
    }

    private fun appendRaw(text: String) {
        writer.enqueue(text)
    }

    companion object {
        const val DIR_NAME = "debug"
        const val CURRENT_FILE = "vpn_debug.txt"
        const val PREVIOUS_FILE = "vpn_debug.prev.txt"
        const val MAX_FILE_BYTES = 8L * 1024L * 1024L
        private const val TAG = "VpnDebug"
        private const val TAG_EVENT = "VpnEvent"

        @Volatile
        private var instance: VpnDebugLogger? = null

        fun install(logger: VpnDebugLogger) {
            instance = logger
        }

        fun get(): VpnDebugLogger? = instance

        fun event(category: String, action: String, details: Map<String, Any?> = emptyMap()) {
            get()?.event(category, action, details)
        }

        fun d(tag: String, message: String) {
            get()?.d(tag, message) ?: Log.d(tag, message)
        }

        fun i(tag: String, message: String) {
            get()?.i(tag, message) ?: Log.i(tag, message)
        }

        fun w(tag: String, message: String, error: Throwable? = null) {
            get()?.w(tag, message, error) ?: if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
        }

        fun e(tag: String, message: String, error: Throwable? = null) {
            get()?.e(tag, message, error) ?: if (error != null) Log.e(tag, message, error) else Log.e(tag, message)
        }
    }
}
