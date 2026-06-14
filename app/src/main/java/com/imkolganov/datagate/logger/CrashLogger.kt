package com.imkolganov.datagate.logger

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.imkolganov.datagate.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.system.exitProcess

class CrashLogger(private val context: Context) {

    private val dir: File by lazy {
        File(context.noBackupFilesDir, "crash").apply { mkdirs() }
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
        runCatching {
            writeReport(
                kind = "nonfatal",
                thread = Thread.currentThread(),
                throwable = throwable,
                extras = data
            )
            CrashUploadWorkScheduler.enqueue(context)
        }
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
            appendLine("app_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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

    internal fun crashDir(): File = dir
}
