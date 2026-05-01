package com.imkolganov.datagate.logger

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

internal class CrashReportUploader(
    private val crashDir: File,
    private val httpClient: OkHttpClient,
    private val processNameProvider: () -> String,
    private val eventLogger: (String) -> Unit = {}
) {
    data class BatchResult(
        val outcome: Outcome,
        val retryAfterSeconds: Long? = null
    )

    enum class Outcome {
        COMPLETED,
        RETRY_LATER,
    }

    fun uploadBatch(
        endpointUrl: String,
        crashReportToken: String?,
        maxFiles: Int = DEFAULT_MAX_FILES
    ): BatchResult {
        if (!singleFlight.compareAndSet(false, true)) {
            eventLogger("Crash upload already in progress, skipping parallel run")
            return BatchResult(Outcome.RETRY_LATER)
        }
        return try {
            uploadBatchLocked(endpointUrl, crashReportToken, maxFiles)
        } finally {
            singleFlight.set(false)
        }
    }

    private fun uploadBatchLocked(
        endpointUrl: String,
        crashReportToken: String?,
        maxFiles: Int
    ): BatchResult {
        val files = crashDir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L && it.extension == "txt" && !it.name.startsWith("tmp_") }
            ?.sortedBy { it.name }
            ?.take(maxFiles)
            ?.toList()
            .orEmpty()

        val mediaType = "text/plain; charset=utf-8".toMediaType()
        for (file in files) {
            val payload = runCatching { file.readText() }.getOrElse { readError ->
                eventLogger("Skip unreadable crash file=${file.name}: ${readError.javaClass.simpleName}")
                continue
            }

            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("Content-Type", "text/plain; charset=utf-8")
                .addHeader("X-Crash-Filename", file.name)
                .addHeader("X-Crash-Process", processNameProvider())
                .post(payload.toRequestBody(mediaType))
                .apply {
                    val token = crashReportToken?.trim().orEmpty()
                    if (token.isNotEmpty()) {
                        addHeader("X-Crash-Token", token)
                    }
                }
                .build()

            val result = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val code = response.code
                    when {
                        code in 200..299 -> HttpAction.DeleteAndContinue
                        code == 400 || code == 413 -> HttpAction.DeleteAndContinue
                        code == 429 -> HttpAction.RetryAndStop(parseRetryAfterSeconds(response.header("Retry-After")))
                        code in 500..599 -> HttpAction.RetryAndStop()
                        else -> HttpAction.RetryAndStop()
                    }.also {
                        eventLogger(
                            "Crash upload file=${file.name} bytes=${payload.toByteArray().size} code=$code action=$it"
                        )
                    }
                }
            }.getOrElse { networkError ->
                eventLogger(
                    "Crash upload failed file=${file.name}: ${networkError.javaClass.simpleName}"
                )
                return BatchResult(Outcome.RETRY_LATER)
            }

            when (result) {
                is HttpAction.DeleteAndContinue -> {
                    runCatching { file.delete() }.onFailure {
                        eventLogger("Failed to delete uploaded crash file=${file.name}")
                    }
                }

                is HttpAction.RetryAndStop -> {
                    return BatchResult(
                        outcome = Outcome.RETRY_LATER,
                        retryAfterSeconds = result.retryAfterSeconds
                    )
                }
            }
        }

        return BatchResult(Outcome.COMPLETED)
    }

    private fun parseRetryAfterSeconds(headerValue: String?): Long? {
        val raw = headerValue?.trim().orEmpty()
        if (raw.isEmpty()) return null
        raw.toLongOrNull()?.takeIf { it > 0L }?.let { return it }

        val retryAt = runCatching { retryAfterDateFormat.parse(raw) }.getOrNull() ?: return null
        val deltaSeconds = ((retryAt.time - Date().time) / 1000L)
        return deltaSeconds.takeIf { it > 0L }
    }

    private sealed class HttpAction {
        data object DeleteAndContinue : HttpAction()
        data class RetryAndStop(val retryAfterSeconds: Long? = null) : HttpAction()
    }

    companion object {
        private const val DEFAULT_MAX_FILES = 30
        private val singleFlight = AtomicBoolean(false)
        private val retryAfterDateFormat = SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
    }
}
