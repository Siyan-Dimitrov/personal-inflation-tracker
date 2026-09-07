package com.siyandimitrov.pocketindex.extraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.siyandimitrov.pocketindex.BuildConfig
import com.siyandimitrov.pocketindex.data.preferences.InflationPreferences
import com.siyandimitrov.pocketindex.data.preferences.VisionProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the receipt with a vision model over the Ollama API when a server is configured, and
 * hands the photo to on-device OCR whenever that is off, unreachable, unparseable, or produces
 * lines that do not add up to the printed total. The photo goes only to the server the user
 * typed in; with no server set, nothing leaves the phone.
 */
class VisionFirstReceiptOcrService(
    private val preferences: InflationPreferences,
    private val fallback: ReceiptOcrService,
) : ReceiptOcrService {

    override suspend fun recognise(imageUri: Uri): OcrResult {
        val settings = preferences.visionSettings
        if (!settings.isEnabled) return fallback.recognise(imageUri).copy(readBy = ON_DEVICE)
        val attempts = mutableListOf<VisionReceipt>()
        var failure: String? = null
        withContext(Dispatchers.IO) {
            val claude = settings.provider == VisionProvider.CLAUDE
            val body = runCatching {
                val encoded = encodeForUpload(File(requireNotNull(imageUri.path)))
                if (claude) buildClaudeRequestBody(settings.activeModel, encoded) else buildVisionRequestBody(settings.model, encoded)
            }.getOrElse { failure = it.message ?: it.javaClass.simpleName; return@withContext }
            val url = if (claude) CLAUDE_MESSAGES_URL else "${settings.serverUrl.trimEnd('/')}/api/chat"
            val headers = if (claude) {
                mapOf("Authorization" to "Bearer ${settings.claudeApiKey}", "anthropic-version" to CLAUDE_API_VERSION)
            } else {
                settings.apiKey.takeIf(String::isNotBlank)?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
            }
            // The hosted models answer differently to the same photo, so one more try is often
            // all a reading that does not add up needs.
            for (attempt in 1..MAX_ATTEMPTS) {
                val reading = runCatching { post(url, headers, body) }
                    .onFailure { failure = it.message ?: it.javaClass.simpleName }
                    .getOrNull()
                    ?.let { response ->
                        (if (claude) parseClaudeReceipt(response) else parseVisionReceipt(response))
                            .also { if (it == null) failure = "the reply could not be parsed" }
                    }
                if (reading != null) {
                    attempts += reading
                    if (reading.repairs().any { it.reconciles() }) break
                }
            }
        }
        val chosen = chooseVisionReading(attempts)
        if (chosen == null) {
            Log.w(TAG, "Vision read failed ($failure); using on-device OCR.")
            return fallback.recognise(imageUri).copy(readBy = "$ON_DEVICE; AI read failed: $failure")
        }
        val gap = chosen.discrepancyMinor()?.takeIf { abs(it) > RECONCILE_TOLERANCE_MINOR }
        if (gap != null) Log.w(TAG, "Vision read kept for review; lines are $gap pence off the total.")
        return chosen.toOcrResult().copy(
            readBy = "AI (${settings.activeModel})" +
                (gap?.let { ", lines £${abs(it).asPounds()} ${if (it < 0) "short of" else "over"} the printed total" } ?: ""),
        )
    }

    private fun post(url: String, headers: Map<String, String>, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            // A PC running a local model can take a minute or more per photo.
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            // ollama.com answers 403 to Android's default "Dalvik/..." user agent.
            connection.setRequestProperty("User-Agent", "PocketIndex/${BuildConfig.VERSION_NAME}")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                val detail = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }
                    .getOrNull()?.trim()?.take(MAX_ERROR_DETAIL)
                "HTTP ${connection.responseCode}" + (detail?.takeIf(String::isNotEmpty)?.let { ": $it" } ?: "")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** A phone photo is several megabytes; the model needs nothing near that to read a till roll. */
    private fun encodeForUpload(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PIXELS) {
            sample *= 2
        }
        val bitmap = checkNotNull(
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }),
        ) { "The receipt image could not be decoded." }
        return try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TAG = "VisionReceiptOcr"
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 180_000
        const val MAX_ATTEMPTS = 2
        const val CLAUDE_MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        const val CLAUDE_API_VERSION = "2023-06-01"
        const val MAX_ERROR_DETAIL = 160
        const val RECONCILE_TOLERANCE_MINOR = 2
        const val ON_DEVICE = "On device"
        const val MAX_EDGE_PIXELS = 1_600
        const val JPEG_QUALITY = 85
    }
}

private fun Int.asPounds(): String =
    (if (this < 0) "-" else "") + "%d.%02d".format(java.util.Locale.ROOT, abs(this) / 100, abs(this) % 100)
