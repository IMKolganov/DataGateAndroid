package com.imkolganov.datagate.logger

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.imkolganov.datagate.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CrashUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val endpointUrl = resolveCrashEndpoint(BuildConfig.BACKEND_BASE_URL)
        if (endpointUrl == null) {
            Log.w(TAG, "Skip crash upload: invalid BACKEND_BASE_URL")
            return@withContext Result.success()
        }

        val uploader = CrashReportUploader(
            crashDir = CrashLogger(applicationContext).crashDir(),
            httpClient = httpClient,
            processNameProvider = { BuildConfig.APPLICATION_ID },
            eventLogger = { Log.i(TAG, it) }
        )

        val batch = uploader.uploadBatch(
            endpointUrl = endpointUrl,
            crashReportToken = BuildConfig.CRASH_REPORT_TOKEN,
            maxFiles = MAX_FILES_PER_RUN
        )

        if (batch.outcome == CrashReportUploader.Outcome.COMPLETED) {
            return@withContext Result.success()
        }

        val retryAfter = batch.retryAfterSeconds
        if (retryAfter != null && retryAfter > 0L) {
            CrashUploadWorkScheduler.enqueue(applicationContext, initialDelaySeconds = retryAfter)
            return@withContext Result.success()
        }

        return@withContext Result.retry()
    }

    private fun resolveCrashEndpoint(backendBaseUrl: String): String? {
        val parsed = runCatching { Uri.parse(backendBaseUrl.trim()) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.trim().orEmpty()
        val host = parsed.host?.trim().orEmpty()
        if (scheme.isEmpty() || host.isEmpty()) return null

        val authority = if (parsed.port > 0) "$host:${parsed.port}" else host
        return "$scheme://$authority$CRASH_INGEST_PATH"
    }

    private companion object {
        private const val TAG = "CrashUploadWorker"
        private const val CRASH_INGEST_PATH = "/api/v1/mobile/crash-ingest"
        private const val MAX_FILES_PER_RUN = 30
        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .build()
        }
    }
}
