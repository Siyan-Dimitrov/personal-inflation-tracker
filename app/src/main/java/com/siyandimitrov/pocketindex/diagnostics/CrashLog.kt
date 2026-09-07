package com.siyandimitrov.pocketindex.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.siyandimitrov.pocketindex.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What Settings shows and shares: the recorded crashes plus what the system says about exits. */
data class CrashReport(val crashCount: Int, val text: String)

/**
 * Records every uncaught exception to a private file so a crash on a phone with no debugging
 * cable can still be read afterwards, from Settings → Crash log. The system's own record of
 * why the process last died (ANR, low memory, native crash) is added when the report is built,
 * since those never reach an exception handler.
 */
object CrashLog {
    private const val FILE_NAME = "crash.log"
    private const val ENTRY_MARKER = "=== "

    /** Keeps the file, and the share intent it ends up in, comfortably small. */
    internal const val MAX_BYTES = 200_000

    fun install(context: Context) {
        val file = file(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { append(file, entry(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun report(context: Context): CrashReport {
        val crashes = file(context).takeIf(File::exists)?.readText().orEmpty()
        val text = buildString {
            append("Pocket Index ").append(BuildConfig.VERSION_NAME)
            append(" on ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            append(", Android ").append(Build.VERSION.RELEASE).append("\n\n")
            append("## Uncaught exceptions\n")
            append(crashes.ifEmpty { "None recorded.\n" })
            append("\n## Recent process exits reported by the system\n")
            append(systemExits(context).ifEmpty { "None available.\n" })
        }
        return CrashReport(crashCount = countEntries(crashes), text = text)
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    internal fun countEntries(crashes: String): Int =
        crashes.lineSequence().count { it.startsWith(ENTRY_MARKER) }

    /** Appends [entry], dropping the oldest text once the file passes [MAX_BYTES]. */
    internal fun append(file: File, entry: String) {
        val existing = if (file.exists()) file.readText() else ""
        val combined = existing + entry
        file.writeText(
            if (combined.length <= MAX_BYTES) {
                combined
            } else {
                // Cut at an entry boundary so the first entry kept is a whole one.
                val cut = combined.indexOf("\n$ENTRY_MARKER", combined.length - MAX_BYTES)
                if (cut >= 0) combined.substring(cut + 1) else entry.takeLast(MAX_BYTES)
            },
        )
    }

    private fun entry(thread: Thread, error: Throwable): String =
        ENTRY_MARKER + timestamp(System.currentTimeMillis()) + " on thread " + thread.name +
            "\n" + error.stackTraceToString() + "\n"

    private fun systemExits(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val manager = context.getSystemService(ActivityManager::class.java) ?: return ""
        val exits = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_SYSTEM_EXITS)
        }.getOrDefault(emptyList())
        return exits.joinToString("") { exit ->
            buildString {
                append(ENTRY_MARKER).append(timestamp(exit.timestamp))
                append(' ').append(reasonName(exit.reason))
                exit.description?.let { append(": ").append(it) }
                append('\n')
                if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                    runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }
                        .getOrNull()
                        ?.let { trace -> append(trace.take(MAX_ANR_TRACE)).append('\n') }
                }
            }
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialisation failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
        ApplicationExitInfo.REASON_SIGNALED -> "signalled"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
        ApplicationExitInfo.REASON_OTHER -> "other"
        else -> "reason $reason"
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(millis))

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private const val MAX_SYSTEM_EXITS = 10
    private const val MAX_ANR_TRACE = 20_000
}
