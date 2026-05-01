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

    /**
     * Set when the user must enable "Install unknown apps" first; cleared after install UI starts or file missing.
     */
    @Volatile
    private var pendingApkFile: File? = null

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

    sealed class InstallUiResult {
        /** System package installer activity was started. */
        data object InstallerStarted : InstallUiResult()

        /** Opened app settings so the user can allow installs; [tryContinuePendingInstall] will open the installer when they return. */
        data object OpenedInstallPermissionSettings : InstallUiResult()

        data object Failed : InstallUiResult()
    }

    /**
     * Opens package installer. On Android O+, may open [Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES] first;
     * then [tryContinuePendingInstall] must run (e.g. from [Activity.onResume]) to launch the installer.
     */
    fun startInstall(activity: Activity, apkFile: File): InstallUiResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pm = activity.packageManager
            if (!pm.canRequestPackageInstalls()) {
                pendingApkFile = apkFile
                return try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                    InstallUiResult.OpenedInstallPermissionSettings
                } catch (e: Exception) {
                    pendingApkFile = null
                    Log.e(TAG, "Cannot open unknown sources settings", e)
                    InstallUiResult.Failed
                }
            }
        }

        val result = launchPackageInstallerActivity(activity, apkFile)
        if (result is InstallUiResult.InstallerStarted) pendingApkFile = null
        return result
    }

    /**
     * Call when the activity becomes visible again after the user may have allowed installs from this app.
     * [onInstallerStarted] runs on the main thread when the system install UI is shown.
     */
    fun tryContinuePendingInstall(
        activity: Activity,
        onInstallerStarted: () -> Unit = {},
    ) {
        val apk = pendingApkFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return
        }
        if (!apk.exists() || apk.length() == 0L) {
            pendingApkFile = null
            return
        }
        when (val r = launchPackageInstallerActivity(activity, apk)) {
            is InstallUiResult.InstallerStarted -> {
                pendingApkFile = null
                onInstallerStarted()
            }
            is InstallUiResult.Failed -> pendingApkFile = null
            is InstallUiResult.OpenedInstallPermissionSettings -> { /* should not happen from here */ }
        }
    }

    private fun launchPackageInstallerActivity(activity: Activity, apkFile: File): InstallUiResult {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            activity.startActivity(intent)
            InstallUiResult.InstallerStarted
        } catch (e: Exception) {
            Log.e(TAG, "Cannot start installer", e)
            InstallUiResult.Failed
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
