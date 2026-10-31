package com.imkolganov.datagate.update

data class ApkDownloadProgress(
    val bytesRead: Long,
    val contentLength: Long,
) {
    val percent: Int? get() = ApkDownloadProgressPolicy.percent(bytesRead, contentLength)
    val fraction: Float? get() = ApkDownloadProgressPolicy.fraction(bytesRead, contentLength)
}

internal sealed class ApkDownloadBar {
    data class Determinate(val fraction: Float, val percent: Int) : ApkDownloadBar()
    data object Indeterminate : ApkDownloadBar()
}

/** Maps HTTP byte counts onto a determinate or indeterminate APK download bar. */
internal object ApkDownloadProgressPolicy {
    const val UNKNOWN_LENGTH = -1L
    const val INDETERMINATE_MIN_DELTA_BYTES = 256L * 1024L

    fun percent(bytesRead: Long, contentLength: Long): Int? {
        if (contentLength <= 0L || bytesRead < 0L) return null
        return ((bytesRead * 100L) / contentLength).coerceIn(0L, 100L).toInt()
    }

    fun fraction(bytesRead: Long, contentLength: Long): Float? {
        val p = percent(bytesRead, contentLength) ?: return null
        return p / 100f
    }

    fun bar(progress: ApkDownloadProgress?): ApkDownloadBar {
        val percent = progress?.percent
        val fraction = progress?.fraction
        if (percent == null || fraction == null) return ApkDownloadBar.Indeterminate
        return ApkDownloadBar.Determinate(fraction = fraction, percent = percent)
    }

    fun isComplete(bytesRead: Long, contentLength: Long): Boolean {
        if (bytesRead <= 0L) return false
        if (contentLength > 0L && bytesRead != contentLength) return false
        return true
    }

    fun finishedProgress(bytesRead: Long, contentLength: Long): ApkDownloadProgress {
        return ApkDownloadProgress(
            bytesRead = bytesRead,
            contentLength = if (contentLength > 0L) contentLength else bytesRead,
        )
    }

    fun shouldPublish(previous: ApkDownloadProgress?, next: ApkDownloadProgress): Boolean {
        if (previous == null) return true
        val nextPercent = next.percent
        if (nextPercent != null) {
            return previous.percent != nextPercent
        }
        return next.bytesRead - previous.bytesRead >= INDETERMINATE_MIN_DELTA_BYTES
    }
}
