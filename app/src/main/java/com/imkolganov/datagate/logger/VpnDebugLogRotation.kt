package com.imkolganov.datagate.logger

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Pure helpers for [VpnDebugLogger] so rotation/format can be unit-tested without Android. */
internal object VpnDebugLogRotation {

    fun shouldRotate(fileLengthBytes: Long, maxBytes: Long = VpnDebugLogger.MAX_FILE_BYTES): Boolean =
        fileLengthBytes >= maxBytes

    fun rotate(dir: File, current: File) {
        val previous = File(dir, VpnDebugLogger.PREVIOUS_FILE)
        if (previous.exists()) {
            previous.delete()
        }
        if (!current.renameTo(previous)) {
            previous.writeText(current.readText())
            current.writeText("")
        }
        File(dir, VpnDebugLogger.CURRENT_FILE).appendText(
            "==== rotated at ${isoUtc(Date())} (previous kept as ${VpnDebugLogger.PREVIOUS_FILE}) ====\n"
        )
    }

    fun formatLine(
        timestampUtc: String,
        level: String,
        tag: String,
        threadName: String,
        message: String,
        error: Throwable?,
    ): String = buildString {
        append(timestampUtc)
        append(' ')
        append(level)
        append('/')
        append(tag)
        append(" [")
        append(threadName)
        append("] ")
        append(message.replace('\n', ' '))
        if (error != null) {
            append(" | ")
            append(error.javaClass.name)
            append(": ")
            append(error.message ?: "")
            append('\n')
            append(stackTraceOf(error))
        }
        append('\n')
    }

    fun isoUtc(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }

    fun stackTraceOf(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        return sw.toString().trimEnd()
    }
}
