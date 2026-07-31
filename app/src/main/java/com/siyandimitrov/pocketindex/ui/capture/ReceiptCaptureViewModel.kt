package com.siyandimitrov.pocketindex.ui.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.repository.NewReceipt
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.extraction.ReceiptExtractionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReceiptCaptureUiState(
    val isProcessing: Boolean = false,
    val completedReceiptId: Long? = null,
    val message: String? = null,
)

@HiltViewModel
class ReceiptCaptureViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val receiptRepository: ReceiptRepository,
    private val extractionScheduler: ReceiptExtractionScheduler,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ReceiptCaptureUiState())
    val uiState: StateFlow<ReceiptCaptureUiState> = mutableUiState.asStateFlow()

    fun processScan(sourceUri: Uri) {
        if (mutableUiState.value.isProcessing) return
        mutableUiState.update {
            it.copy(isProcessing = true, completedReceiptId = null, message = null)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    processScanInternal(sourceUri)
                }
            }.onSuccess { receiptId ->
                mutableUiState.value = ReceiptCaptureUiState(
                    completedReceiptId = receiptId,
                    message = "Receipt saved. It will keep processing in the background.",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                mutableUiState.value = ReceiptCaptureUiState(
                    message = error.userFacingMessage(),
                )
            }
        }
    }

    fun reportScannerFailure(error: Throwable) {
        mutableUiState.value = ReceiptCaptureUiState(
            message = error.userFacingMessage(),
        )
    }

    fun clearEvent() {
        mutableUiState.update { it.copy(completedReceiptId = null, message = null) }
    }

    private suspend fun processScanInternal(sourceUri: Uri): Long {
        val savedFile = savePrivateCopy(sourceUri)
        val purchasedAt = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        val receiptId = receiptRepository.createPendingReceipt(
            NewReceipt(
                imagePath = savedFile.absolutePath,
                purchasedAt = purchasedAt,
            ),
        )
        extractionScheduler.enqueue(receiptId)
        return receiptId
    }

    private fun savePrivateCopy(sourceUri: Uri): File {
        val receiptDirectory = File(context.filesDir, "receipts").apply {
            check(exists() || mkdirs()) { "Could not create private receipt storage." }
        }
        val target = File.createTempFile("receipt-", ".jpg", receiptDirectory)
        try {
            val input = checkNotNull(context.contentResolver.openInputStream(sourceUri)) {
                "The scanned receipt could not be opened."
            }
            input.use { source ->
                target.outputStream().buffered().use(source::copyTo)
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        return target
    }
}

private fun Throwable.userFacingMessage(): String =
    when {
        message?.contains("unsupported", ignoreCase = true) == true ->
            "Receipt scanning is not supported on this device. Add the receipt manually instead."
        message?.contains("No purchasable line items", ignoreCase = true) == true ->
            "No line items were found. Try scanning again with the receipt flat and well lit."
        else -> "The receipt could not be processed. Please try scanning it again."
    }
