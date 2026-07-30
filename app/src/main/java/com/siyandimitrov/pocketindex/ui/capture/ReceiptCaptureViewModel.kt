package com.siyandimitrov.pocketindex.ui.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.ExtractedLineItem
import com.siyandimitrov.pocketindex.data.repository.NewReceipt
import com.siyandimitrov.pocketindex.data.repository.ReceiptExtraction
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.extraction.BaseUnit
import com.siyandimitrov.pocketindex.extraction.ProductCandidate
import com.siyandimitrov.pocketindex.extraction.ReceiptExtractor
import com.siyandimitrov.pocketindex.extraction.ReceiptOcrService
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class ReceiptCaptureUiState(
    val isProcessing: Boolean = false,
    val completedReceiptId: Long? = null,
    val message: String? = null,
)

@HiltViewModel
class ReceiptCaptureViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val receiptRepository: ReceiptRepository,
    private val catalogRepository: CatalogRepository,
    private val ocrService: ReceiptOcrService,
    private val extractor: ReceiptExtractor,
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
                    message = "Receipt scanned. Check the highlighted items.",
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

        val ocrResult = ocrService.recognise(Uri.fromFile(savedFile))
        val products = catalogRepository.observeProducts().first()
        val candidates = products.map { productWithCategory ->
            val product = productWithCategory.product
            ProductCandidate(
                id = product.id,
                canonicalName = product.canonicalName,
                aliases = product.aliases.toAliases(),
                packSize = product.packSize,
                baseUnit = product.unitType.toBaseUnit(),
            )
        }
        val result = extractor.extract(ocrResult, candidates)
        check(result.lineItems.isNotEmpty()) {
            "No purchasable line items could be read from this receipt."
        }
        val merchantName = result.merchantName
            ?.trim()
            ?.take(80)
            ?.takeIf(String::isNotBlank)
        val merchantId = merchantName
            ?.let { merchantName ->
                val existing = catalogRepository.observeMerchants().first()
                    .firstOrNull { it.name.equals(merchantName, ignoreCase = true) }
                existing?.id ?: catalogRepository.addMerchant(
                    MerchantEntity(name = merchantName),
                )
            }
        val lineItems = result.lineItems.map { item ->
            ExtractedLineItem(
                rawText = item.rawText,
                quantity = item.quantity,
                unitPriceMinor = (item.unitPriceMinor ?: item.lineTotalMinor).toLong(),
                lineTotalMinor = item.lineTotalMinor.toLong(),
                productId = item.productMatch?.productId,
                matchConfidence = item.productMatch?.confidence,
            )
        }
        val calculatedTotal = lineItems.sumOf(ExtractedLineItem::lineTotalMinor)
        receiptRepository.saveExtraction(
            receiptId = receiptId,
            extraction = ReceiptExtraction(
                merchantId = merchantId,
                subtotalMinor = result.totals.subtotalMinor?.toLong(),
                taxMinor = result.totals.taxMinor?.toLong(),
                totalMinor = result.totals.totalMinor?.toLong() ?: calculatedTotal,
                ocrText = result.rawOcrText,
                status = ReceiptStatus.NEEDS_REVIEW,
                lineItems = lineItems,
            ),
        )
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

private fun String.toAliases(): List<String> = runCatching {
    val json = JSONArray(this)
    buildList(json.length()) {
        repeat(json.length()) { index ->
            add(json.optString(index))
        }
    }
}.getOrDefault(emptyList())

private fun UnitType.toBaseUnit(): BaseUnit = when (this) {
    UnitType.MASS_G -> BaseUnit.MASS_G
    UnitType.VOLUME_ML -> BaseUnit.VOLUME_ML
    UnitType.COUNT, UnitType.SERVICE -> BaseUnit.COUNT
}

private fun Throwable.userFacingMessage(): String =
    when {
        message?.contains("unsupported", ignoreCase = true) == true ->
            "Receipt scanning is not supported on this device. Add the receipt manually instead."
        message?.contains("No purchasable line items", ignoreCase = true) == true ->
            "No line items were found. Try scanning again with the receipt flat and well lit."
        else -> "The receipt could not be processed. Please try scanning it again."
    }
