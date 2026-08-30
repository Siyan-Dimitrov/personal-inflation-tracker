package com.siyandimitrov.pocketindex.extraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.siyandimitrov.pocketindex.data.preferences.InflationPreferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
        if (!settings.isEnabled) return fallback.recognise(imageUri)
        val receipt = withContext(Dispatchers.IO) {
            runCatching {
                val encoded = encodeForUpload(File(requireNotNull(imageUri.path)))
                val body = buildVisionRequestBody(settings.model, encoded)
                parseVisionReceipt(post(settings.serverUrl, settings.apiKey, body))
            }.onFailure { Log.w(TAG, "Vision read failed; using on-device OCR.", it) }
                .getOrNull()
        }
        return when {
            receipt == null -> fallback.recognise(imageUri)
            !receipt.reconciles() -> {
                Log.w(TAG, "Vision read did not reconcile with its total; using on-device OCR.")
                fallback.recognise(imageUri)
            }
            else -> receipt.toOcrResult()
        }
    }

    private fun post(serverUrl: String, apiKey: String, body: String): String {
        val connection = URL("${serverUrl.trimEnd('/')}/api/chat")
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            // A PC running a local model can take a minute or more per photo.
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Vision server returned HTTP ${connection.responseCode}."
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
        const val MAX_EDGE_PIXELS = 1_600
        const val JPEG_QUALITY = 85
    }
}
