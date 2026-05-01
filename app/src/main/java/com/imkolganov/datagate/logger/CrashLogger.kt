package com.imkolganov.datagate.logger

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class CrashLogger(private val context: Context) {

    private val dir: File by lazy {
        File(context.noBackupFilesDir, "crash").apply { mkdirs() }
    }
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashReport(thread, throwable)
            } catch (_: Throwable) {
                // Intentionally ignore: never crash while logging a crash.
            } finally {
                // Delegate to the previous handler (usually the system handler).
                previous?.uncaughtException(thread, throwable)
                    ?: run {
                        // Fallback: if no previous handler, kill the process to avoid undefined state.
                        android.os.Process.killProcess(android.os.Process.myPid())
                        exitProcess(10)
                    }
            }
        }
    }

    fun logNonFatal(tag: String, throwable: Throwable, extras: Map<String, String> = emptyMap()) {
        val data = linkedMapOf<String, String>()
        data["tag"] = tag
        extras.forEach { (k, v) -> data[k] = v }
        writeReport(
            kind = "nonfatal",
            thread = Thread.currentThread(),
            throwable = throwable,
            extras = data
        )
    }

    fun uploadPendingAsync(endpointUrl: String, maxFiles: Int = 30) {
        if (endpointUrl.isBlank()) return
        if (!isValidHttpsUrl(endpointUrl)) return
        Thread {
            runCatching { uploadPending(endpointUrl, maxFiles) }
        }.start()
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        writeReport(
            kind = "fatal",
            thread = thread,
            throwable = throwable
        )
    }

    @Synchronized
    private fun writeReport(
        kind: String,
        thread: Thread,
        throwable: Throwable,
        extras: Map<String, String> = emptyMap()
    ) {
        val now = Date()
        val ts = isoUtc(now)
        val file = File(dir, "${kind}_$ts.txt")

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stack = sw.toString()

        val processName = getProcessNameCompat(context)

        val text = buildString {
            appendLine("timestamp_utc=$ts")
            appendLine("process=$processName")
            appendLine("thread=${thread.name}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("kind=$kind")
            appendLine("exception=${throwable::class.java.name}")
            appendLine("message=${throwable.message ?: ""}")
            if (extras.isNotEmpty()) {
                extras.forEach { (k, v) -> appendLine("$k=$v") }
            }
            appendLine()
            appendLine(stack)
        }

        val tmp = File(dir, "tmp_${kind}_$ts.txt")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun isoUtc(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }

    private fun getProcessNameCompat(context: Context): String {
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val proc = am?.runningAppProcesses?.firstOrNull { it.pid == pid }
        return proc?.processName ?: "unknown"
    }

    @Synchronized
    private fun uploadPending(endpointUrl: String, maxFiles: Int) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension == "txt" && !it.name.startsWith("tmp_") }
            ?.sortedBy { it.lastModified() }
            ?.take(maxFiles)
            ?: return

        val mediaType = "text/plain; charset=utf-8".toMediaType()
        val processName = getProcessNameCompat(context)
        for (file in files) {
            val content = runCatching { file.readText() }.getOrNull() ?: continue
            val request = Request.Builder()
                .url(endpointUrl)
                .addHeader("X-Crash-Filename", file.name)
                .addHeader("X-Crash-Process", processName)
                .post(content.toRequestBody(mediaType))
                .build()

            val success = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            }.getOrDefault(false)

            if (success) {
                runCatching { file.delete() }
            } else {
                // Stop here: avoid hammering endpoint when offline/unhealthy.
                break
            }
        }
    }

    private fun isValidHttpsUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme?.lowercase(Locale.US) == "https" && !uri.host.isNullOrBlank()
    }
}
