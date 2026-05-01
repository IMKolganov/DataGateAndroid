package com.imkolganov.datagate.logger

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object CrashUploadWorkScheduler {
    private const val WORK_NAME = "crash_upload_batch"
    private const val RETRY_DELAY_SECONDS = 15L

    fun enqueue(context: Context, initialDelaySeconds: Long = 0L) {
        val delaySeconds = initialDelaySeconds.coerceAtLeast(0L)
        val requestBuilder = OneTimeWorkRequestBuilder<CrashUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_DELAY_SECONDS,
                TimeUnit.SECONDS
            )

        if (delaySeconds > 0L) {
            requestBuilder.setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            requestBuilder.build()
        )
    }
}
