package com.siyandimitrov.pocketindex.ui.receipts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.ConfirmedLineItem
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.extraction.parseReceiptPackSize
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductChoiceUi(
    val id: Long,
    val name: String,
    val packSize: Double?,
    val unitType: UnitType,
)

data class ReviewLineItemUi(
    val id: Long,
    /** Immutable OCR evidence shown separately from the selected canonical product. */
    val rawText: String,
    val quantityInput: String,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val productId: Long?,
    val productName: String?,
    val packSizeInput: String,
    val unitType: UnitType?,
    val confidence: Double?,
    val wasUserMatched: Boolean,
) {
    val quantity: Double?
        get() = quantityInput.toReceiptNumber()

    val packSize: Double?
        get() = packSizeInput.toReceiptNumber()

    val isComplete: Boolean
        get() = productId != null &&
            (quantity ?: 0.0) > 0.0 &&
            (packSize ?: 0.0) > 0.0 &&
            unitPriceMinor >= 0 &&
            lineTotalMinor >= 0
}

data class ReceiptReviewUiState(
    val receiptId: Long? = null,
    val merchantName: String = "",
    val purchasedAt: String = "",
    val totalMinor: Long = 0,
    val items: List<ReviewLineItemUi> = emptyList(),
    val productChoices: List<ProductChoiceUi> = emptyList(),
    val queueCount: Int = 0,
    val unprocessedCount: Int = 0,
    val isLiveReceipt: Boolean = false,
    val isConfirming: Boolean = false,
    val reconciliationValid: Boolean = false,
    val message: String? = null,
) {
    val confirmEnabled: Boolean
        get() = isLiveReceipt &&
            !isConfirming &&
            reconciliationValid &&
            items.isNotEmpty() &&
            items.all(ReviewLineItemUi::isComplete)
}

private data class ReviewEdit(
    val productId: Long?,
    val quantityInput: String,
    val packSizeInput: String,
    val wasUserMatched: Boolean = false,
)

private data class ReviewTransientState(
    val edits: Map<Long, ReviewEdit> = emptyMap(),
    val isConfirming: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptReviewViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    catalogRepository: CatalogRepository,
) : ViewModel() {
    private val transient = MutableStateFlow(ReviewTransientState())

    private val reviewQueue = receiptRepository.observeReviewQueue()
    private val latestReviewReceipt = reviewQueue.flatMapLatest { queue ->
        // A failed OCR job remains PENDING with its private image, but must not block later reviews.
        queue.firstOrNull { it.lineItemCount > 0 }
            ?.let { receiptRepository.observeReceipt(it.id) }
            ?: flowOf(null)
    }

    val uiState = combine(
        latestReviewReceipt,
        catalogRepository.observeProducts(),
        reviewQueue,
        transient,
    ) { receiptDetails, products, queue, draft ->
        val productChoices = products.map { productWithCategory ->
            ProductChoiceUi(
                id = productWithCategory.product.id,
                name = productWithCategory.product.canonicalName,
                packSize = productWithCategory.product.packSize,
                unitType = productWithCategory.product.unitType,
            )
        }.sortedBy { it.name.lowercase() }
        val productsById = productChoices.associateBy(ProductChoiceUi::id)

        if (receiptDetails == null) {
            ReceiptReviewUiState(
                productChoices = productChoices,
                queueCount = queue.size,
                unprocessedCount = queue.count { it.lineItemCount == 0 },
                isConfirming = draft.isConfirming,
                message = draft.message,
            )
        } else {
            val items = receiptDetails.lineItems.map { item ->
                val storedProduct = item.productId?.let(productsById::get)
                val detectedPack = parseReceiptPackSize(item.rawText)?.amount
                val edit = draft.edits[item.id]
                val selectedProductId = edit?.productId ?: item.productId
                val selectedProduct = selectedProductId?.let(productsById::get)
                ReviewLineItemUi(
                    id = item.id,
                    rawText = item.rawText,
                    quantityInput = edit?.quantityInput ?: item.quantity.toInputString(),
                    unitPriceMinor = item.unitPriceMinor,
                    lineTotalMinor = item.lineTotalMinor,
                    productId = selectedProductId,
                    productName = selectedProduct?.name,
                    packSizeInput = edit?.packSizeInput
                        ?: (detectedPack ?: storedProduct?.packSize)?.toInputString().orEmpty(),
                    unitType = selectedProduct?.unitType,
                    confidence = if (edit?.wasUserMatched == true) 1.0 else item.matchConfidence,
                    wasUserMatched = edit?.wasUserMatched == true,
                )
            }
            val receipt = receiptDetails.receipt
            val reviewedSubtotal = items.sumOf(ReviewLineItemUi::lineTotalMinor)
            val expectedSubtotal = receipt.subtotalMinor
                ?: (receipt.totalMinor - (receipt.taxMinor ?: 0L))
            val subtotalMatches = abs(reviewedSubtotal - expectedSubtotal) <= 2L
            val totalMatches = receipt.taxMinor?.let { tax ->
                abs(expectedSubtotal + tax - receipt.totalMinor) <= 2L
            } ?: true

            ReceiptReviewUiState(
                receiptId = receipt.id,
                merchantName = receiptDetails.merchant?.name ?: "Unknown merchant",
                purchasedAt = receipt.purchasedAt,
                totalMinor = receipt.totalMinor,
                items = items,
                productChoices = productChoices,
                queueCount = queue.size,
                unprocessedCount = queue.count { it.lineItemCount == 0 },
                isLiveReceipt = true,
                isConfirming = draft.isConfirming,
                reconciliationValid = subtotalMatches && totalMatches,
                message = draft.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReceiptReviewUiState(),
    )

    fun updateProduct(lineItemId: Long, productId: Long) {
        val state = uiState.value
        val item = state.items.firstOrNull { it.id == lineItemId } ?: return
        val product = state.productChoices.firstOrNull { it.id == productId } ?: return
        val detectedPack = parseReceiptPackSize(item.rawText)?.amount
        updateEdit(item) {
            copy(
                productId = productId,
                packSizeInput = (detectedPack ?: product.packSize)?.toInputString().orEmpty(),
                wasUserMatched = true,
            )
        }
    }

    fun updateQuantity(lineItemId: Long, input: String) {
        if (!input.isReceiptNumberInput()) return
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        updateEdit(item) { copy(quantityInput = input) }
    }

    fun updatePackSize(lineItemId: Long, input: String) {
        if (!input.isReceiptNumberInput()) return
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        updateEdit(item) { copy(packSizeInput = input) }
    }

    fun dismissMessage() {
        transient.update { it.copy(message = null) }
    }

    fun confirmReceipt() {
        val state = uiState.value
        val receiptId = state.receiptId ?: return
        if (!state.confirmEnabled) return
        transient.update { it.copy(isConfirming = true, message = null) }
        viewModelScope.launch {
            runCatching {
                receiptRepository.confirmReceipt(
                    receiptId = receiptId,
                    items = state.items.map { item ->
                        ConfirmedLineItem(
                            lineItemId = item.id,
                            productId = requireNotNull(item.productId),
                            quantity = requireNotNull(item.quantity),
                            unitPriceMinor = item.unitPriceMinor,
                            lineTotalMinor = item.lineTotalMinor,
                            packSize = requireNotNull(item.packSize),
                            matchConfidence = item.confidence,
                        )
                    },
                )
            }.onSuccess {
                transient.value = ReviewTransientState(
                    message = "Receipt confirmed and prices added to your basket.",
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                transient.update {
                    it.copy(
                        isConfirming = false,
                        message = error.message
                            ?.takeIf(String::isNotBlank)
                            ?: "The receipt could not be confirmed. Check each item and try again.",
                    )
                }
            }
        }
    }

    private fun updateEdit(
        item: ReviewLineItemUi,
        transform: ReviewEdit.() -> ReviewEdit,
    ) {
        transient.update { state ->
            val current = state.edits[item.id] ?: ReviewEdit(
                productId = item.productId,
                quantityInput = item.quantityInput,
                packSizeInput = item.packSizeInput,
                wasUserMatched = item.wasUserMatched,
            )
            state.copy(
                edits = state.edits + (item.id to current.transform()),
                message = null,
            )
        }
    }
}

private fun String.toReceiptNumber(): Double? =
    replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

private fun String.isReceiptNumberInput(): Boolean =
    isEmpty() || matches(Regex("""\d{0,7}(?:[.,]\d{0,3})?"""))

private fun Double.toInputString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
