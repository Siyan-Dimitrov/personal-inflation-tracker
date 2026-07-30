package com.siyandimitrov.pocketindex.extraction

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface ReceiptOcrService {
    suspend fun recognise(imageUri: Uri): OcrResult
}

/**
 * On-device Latin OCR backed by the bundled ML Kit text recognition model.
 *
 * The caller owns the URI permission for [imageUri]. Keep one instance for the application's
 * lifetime and call [close] when its scope ends.
 */
class MlKitReceiptOcrService(
    context: Context,
    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
) : ReceiptOcrService, Closeable {
    private val applicationContext = context.applicationContext

    override suspend fun recognise(imageUri: Uri): OcrResult {
        val image = InputImage.fromFilePath(applicationContext, imageUri)
        val recognised = recognizer.process(image).awaitResult()
        val lines = recognised.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val bounds = line.boundingBox
                OcrLine(
                    text = line.text,
                    boundingBox = bounds?.let {
                        OcrBoundingBox(
                            left = it.left,
                            top = it.top,
                            right = it.right,
                            bottom = it.bottom,
                        )
                    },
                )
            }
        }
        return OcrResult(text = recognised.text, lines = lines)
    }

    override fun close() {
        recognizer.close()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            continuation.cancel()
        }
    }

