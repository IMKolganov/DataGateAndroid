package com.imkolganov.datagate.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.imkolganov.datagate.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object ApkUpdateInstaller {

    private const val TAG = "ApkUpdateInstaller"

    suspend fun downloadApkToCache(
        activity: Activity,
        http: OkHttpClient,
        downloadUrl: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "datagate-update.apk")
            val client = http.newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(downloadUrl)
                .header(
                    "User-Agent",
                    "DataGate-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
                )
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                response.body!!.byteStream().use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Empty body")
            }
            if (!outFile.exists() || outFile.length() == 0L) error("Empty file")
            outFile
        }
    }

    /**
     * Opens package installer. On Android O+, may need [Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES].
     */
    fun startInstall(activity: Activity, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = activity.packageManager
            if (!pm.canRequestPackageInstalls()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot open unknown sources settings", e)
                }
                return false
            }
        }

        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start installer", e)
            false
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e(TAG, "openUrl failed", e)
        }
    }
}
