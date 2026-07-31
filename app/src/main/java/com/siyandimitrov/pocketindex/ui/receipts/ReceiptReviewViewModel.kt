package com.siyandimitrov.pocketindex.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.ConfirmedLineItem
import com.siyandimitrov.pocketindex.data.repository.NewCatalogProduct
import com.siyandimitrov.pocketindex.data.repository.NewManualLineItem
import com.siyandimitrov.pocketindex.data.repository.NewManualReceipt
import com.siyandimitrov.pocketindex.data.repository.NewObservation
import com.siyandimitrov.pocketindex.data.repository.ObservationRepository
import com.siyandimitrov.pocketindex.data.repository.ReceiptCorrection
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.extraction.parseReceiptPackSize
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductChoiceUi(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val packSize: Double?,
    val unitType: UnitType,
    internal val aliases: String,
)

data class CategoryChoiceUi(
    val id: Long,
    val name: String,
)

data class ReceiptChoiceUi(
    val id: Long,
    val label: String,
    val lineItemCount: Int,
)

data class ReviewLineItemUi(
    val id: Long,
    /** Immutable OCR/manual evidence. Editing this is intentionally unsupported. */
    val rawText: String,
    val quantityInput: String,
    val unitPriceInput: String,
    val lineTotalInput: String,
    val productId: Long?,
    val productName: String?,
    val categoryName: String?,
    val packSizeInput: String,
    val unitType: UnitType?,
    val confidence: Double?,
    val wasUserMatched: Boolean,
    val excluded: Boolean,
) {
    val quantity: Double?
        get() = quantityInput.toReceiptNumber()

    val packSize: Double?
        get() = packSizeInput.toReceiptNumber()

    val unitPriceMinor: Long?
        get() = unitPriceInput.toSignedMinorUnits()

    val lineTotalMinor: Long?
        get() = lineTotalInput.toSignedMinorUnits()

    val isComplete: Boolean
        get() = if (excluded) {
            lineTotalMinor != null && unitPriceMinor != null
        } else {
            productId != null &&
                (quantity ?: 0.0) > 0.0 &&
                (packSize ?: 0.0) > 0.0 &&
                (unitPriceMinor ?: -1L) >= 0L &&
                (lineTotalMinor ?: -1L) >= 0L
        }
}

data class ReceiptReviewUiState(
    val receiptId: Long? = null,
    val selectedReceiptId: Long? = null,
    val merchantInput: String = "",
    val purchasedAtInput: String = "",
    val subtotalInput: String = "",
    val taxInput: String = "",
    val totalInput: String = "",
    val items: List<ReviewLineItemUi> = emptyList(),
    val productChoices: List<ProductChoiceUi> = emptyList(),
    val categoryChoices: List<CategoryChoiceUi> = emptyList(),
    val receiptChoices: List<ReceiptChoiceUi> = emptyList(),
    val queueCount: Int = 0,
    val unprocessedCount: Int = 0,
    val isLiveReceipt: Boolean = false,
    val isEditable: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val reconciliationValid: Boolean = false,
    val reconciliationDifferenceMinor: Long? = null,
    val message: String? = null,
) {
    val totalMinor: Long?
        get() = totalInput.toMinorUnits()

    val subtotalMinor: Long?
        get() = subtotalInput.toMinorUnits()

    val taxMinor: Long?
        get() = taxInput.toMinorUnits()

    val headerComplete: Boolean
        get() = merchantInput.isNotBlank() &&
            purchasedAtInput.isValidIsoDate() &&
            totalMinor != null &&
            (subtotalInput.isBlank() || subtotalMinor != null) &&
            (taxInput.isBlank() || taxMinor != null)

    val includedItems: List<ReviewLineItemUi>
        get() = items.filterNot(ReviewLineItemUi::excluded)

    val confirmEnabled: Boolean
        get() = isEditable &&
            !isSaving &&
            headerComplete &&
            reconciliationValid &&
            includedItems.isNotEmpty() &&
            items.all(ReviewLineItemUi::isComplete)
}

private data class ReviewEdit(
    val productId: Long?,
    val quantityInput: String,
    val unitPriceInput: String,
    val lineTotalInput: String,
    val packSizeInput: String,
    val wasUserMatched: Boolean = false,
    val excluded: Boolean = false,
)

private data class HeaderEdit(
    val receiptId: Long,
    val merchantInput: String,
    val purchasedAtInput: String,
    val subtotalInput: String,
    val taxInput: String,
    val totalInput: String,
)

private data class ReviewTransientState(
    val header: HeaderEdit? = null,
    val edits: Map<Long, ReviewEdit> = emptyMap(),
    val isSaving: Boolean = false,
    val message: String? = null,
)

private data class CatalogSnapshot(
    val products: List<ProductChoiceUi>,
    val categories: List<CategoryChoiceUi>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptReviewViewModel @Inject constructor(
    private val receiptRepository: ReceiptRepository,
    private val catalogRepository: CatalogRepository,
    private val observationRepository: ObservationRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val transient = MutableStateFlow(ReviewTransientState())
    private val selectedReceiptId = MutableStateFlow(savedStateHandle.get<Long>("receiptId"))

    private val reviewQueue = receiptRepository.observeReviewQueue()
    private val selectedReviewReceipt = combine(reviewQueue, selectedReceiptId) { queue, selectedId ->
        selectedId ?: queue.firstOrNull { it.lineItemCount > 0 }?.id
    }.distinctUntilChanged().flatMapLatest { receiptId ->
        receiptId?.let(receiptRepository::observeReceipt) ?: flowOf(null)
    }

    private val catalog = combine(
        catalogRepository.observeProducts(),
        catalogRepository.observeCategories(),
    ) { products, categories ->
        CatalogSnapshot(
            products = products.map { productWithCategory ->
                ProductChoiceUi(
                    id = productWithCategory.product.id,
                    name = productWithCategory.product.canonicalName,
                    categoryId = productWithCategory.category.id,
                    categoryName = productWithCategory.category.name,
                    packSize = productWithCategory.product.packSize,
                    unitType = productWithCategory.product.unitType,
                    aliases = productWithCategory.product.aliases,
                )
            }.sortedBy { it.name.lowercase() },
            categories = categories.map { CategoryChoiceUi(it.id, it.name) },
        )
    }

    val uiState = combine(
        selectedReviewReceipt,
        catalog,
        reviewQueue,
        transient,
    ) { receiptDetails, catalogSnapshot, queue, draft ->
        val productChoices = catalogSnapshot.products
        val productsById = productChoices.associateBy(ProductChoiceUi::id)
        val receiptChoices = queue.map { receipt ->
            ReceiptChoiceUi(
                id = receipt.id,
                label = "${receipt.merchantName ?: "Unknown merchant"} · ${receipt.purchasedAt}",
                lineItemCount = receipt.lineItemCount,
            )
        }

        if (receiptDetails == null) {
            ReceiptReviewUiState(
                selectedReceiptId = selectedReceiptId.value,
                productChoices = productChoices,
                categoryChoices = catalogSnapshot.categories,
                receiptChoices = receiptChoices,
                queueCount = queue.size,
                unprocessedCount = queue.count { it.lineItemCount == 0 },
                isLoading = false,
                isSaving = draft.isSaving,
                message = draft.message,
            )
        } else {
            val receipt = receiptDetails.receipt
            val header = draft.header?.takeIf { it.receiptId == receipt.id }
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
                    unitPriceInput = edit?.unitPriceInput ?: item.unitPriceMinor.toMoneyInput(),
                    lineTotalInput = edit?.lineTotalInput ?: item.lineTotalMinor.toMoneyInput(),
                    productId = selectedProductId,
                    productName = selectedProduct?.name,
                    categoryName = selectedProduct?.categoryName,
                    packSizeInput = edit?.packSizeInput
                        ?: (detectedPack ?: storedProduct?.packSize)?.toInputString().orEmpty(),
                    unitType = selectedProduct?.unitType,
                    confidence = if (edit?.wasUserMatched == true) 1.0 else item.matchConfidence,
                    wasUserMatched = edit?.wasUserMatched == true,
                    excluded = edit?.excluded == true,
                )
            }
            val subtotalInput = header?.subtotalInput
                ?: receipt.subtotalMinor?.toMoneyInput().orEmpty()
            val taxInput = header?.taxInput ?: receipt.taxMinor?.toMoneyInput().orEmpty()
            val totalInput = header?.totalInput ?: receipt.totalMinor.toMoneyInput()
            val subtotalMinor = subtotalInput.toMinorUnits()
            val taxMinor = taxInput.toMinorUnits()
            val totalMinor = totalInput.toMinorUnits()
            val validLineTotals = items.map(ReviewLineItemUi::lineTotalMinor)
            val reviewedSubtotal = validLineTotals.filterNotNull().sum()
            val expectedSubtotal = totalMinor?.let { total ->
                subtotalMinor ?: (total - (taxMinor ?: 0L))
            }
            val difference = expectedSubtotal?.let { reviewedSubtotal - it }
            val headerReconciles = when {
                totalMinor == null || expectedSubtotal == null -> false
                taxInput.isNotBlank() && taxMinor == null -> false
                taxMinor != null -> abs(expectedSubtotal + taxMinor - totalMinor) <= 2L
                else -> true
            }

            ReceiptReviewUiState(
                receiptId = receipt.id,
                selectedReceiptId = selectedReceiptId.value ?: receipt.id,
                merchantInput = header?.merchantInput ?: receiptDetails.merchant?.name.orEmpty(),
                purchasedAtInput = header?.purchasedAtInput ?: receipt.purchasedAt,
                subtotalInput = subtotalInput,
                taxInput = taxInput,
                totalInput = totalInput,
                items = items,
                productChoices = productChoices,
                categoryChoices = catalogSnapshot.categories,
                receiptChoices = receiptChoices,
                queueCount = queue.size,
                unprocessedCount = queue.count { it.lineItemCount == 0 },
                isLiveReceipt = true,
                isEditable = receipt.status != ReceiptStatus.CONFIRMED,
                isLoading = false,
                isSaving = draft.isSaving,
                reconciliationValid = validLineTotals.all { it != null } &&
                    difference != null &&
                    abs(difference) <= 2L &&
                    headerReconciles,
                reconciliationDifferenceMinor = difference,
                message = draft.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReceiptReviewUiState(),
    )

    fun selectReceipt(receiptId: Long) {
        selectedReceiptId.value = receiptId
        savedStateHandle["receiptId"] = receiptId
        transient.value = ReviewTransientState()
    }

    fun updateMerchant(input: String) = updateHeader { copy(merchantInput = input.take(80)) }

    fun updatePurchasedAt(input: String) {
        if (input.length <= 10 && input.all { it.isDigit() || it == '-' }) {
            updateHeader { copy(purchasedAtInput = input) }
        }
    }

    fun updateSubtotal(input: String) = updateMoneyHeader(input) { copy(subtotalInput = it) }

    fun updateTax(input: String) = updateMoneyHeader(input) { copy(taxInput = it) }

    fun updateTotal(input: String) = updateMoneyHeader(input) { copy(totalInput = it) }

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
                excluded = false,
            )
        }
    }

    fun updateQuantity(lineItemId: Long, input: String) =
        updateNumber(lineItemId, input) { copy(quantityInput = it) }

    fun updatePackSize(lineItemId: Long, input: String) =
        updateNumber(lineItemId, input) { copy(packSizeInput = it) }

    fun updateUnitPrice(lineItemId: Long, input: String) =
        updateSignedMoney(lineItemId, input) { copy(unitPriceInput = it) }

    fun updateLineTotal(lineItemId: Long, input: String) =
        updateSignedMoney(lineItemId, input) { copy(lineTotalInput = it) }

    fun setExcluded(lineItemId: Long, excluded: Boolean) {
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        updateEdit(item) { copy(excluded = excluded) }
    }

    fun saveProductForLine(
        lineItemId: Long,
        existingProductId: Long?,
        name: String,
        categoryName: String,
        unitType: UnitType,
        packSizeInput: String,
    ) {
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        val packSize = packSizeInput.toReceiptNumber()
        if (name.isBlank() || categoryName.isBlank() || (packSize ?: 0.0) <= 0.0) {
            showMessage("Product name, category, and a positive pack size are required.")
            return
        }
        val validPackSize = requireNotNull(packSize)
        launchSaving {
            val categoryId = catalogRepository.getOrCreateCategory(categoryName)
            val productId = if (existingProductId == null) {
                catalogRepository.createProduct(
                    NewCatalogProduct(
                        canonicalName = name,
                        categoryId = categoryId,
                        unitType = unitType,
                        packSize = validPackSize,
                    ),
                )
            } else {
                val existing = uiState.value.productChoices
                    .firstOrNull { it.id == existingProductId }
                    ?: error("The selected product no longer exists.")
                catalogRepository.updateProduct(
                    ProductEntity(
                        id = existing.id,
                        canonicalName = name.trim().take(120),
                        categoryId = categoryId,
                        unitType = unitType,
                        packSize = validPackSize,
                        aliases = existing.aliases,
                    ),
                )
                existingProductId
            }
            updateEdit(item) {
                copy(
                    productId = productId,
                    packSizeInput = validPackSize.toInputString(),
                    wasUserMatched = true,
                    excluded = false,
                )
            }
            "Product saved. Confirm the receipt to teach its OCR alias."
        }
    }

    fun addManualLine(
        rawText: String,
        quantityInput: String,
        unitPriceInput: String,
        lineTotalInput: String,
    ) {
        val receiptId = uiState.value.receiptId ?: return
        val quantity = quantityInput.toReceiptNumber()
        val unitPrice = unitPriceInput.toSignedMinorUnits()
        val lineTotal = lineTotalInput.toSignedMinorUnits()
        if (rawText.isBlank() || (quantity ?: 0.0) <= 0.0 || unitPrice == null || lineTotal == null) {
            showMessage("Enter a description, positive quantity, unit price, and line total.")
            return
        }
        val validQuantity = requireNotNull(quantity)
        val validUnitPrice = requireNotNull(unitPrice)
        val validLineTotal = requireNotNull(lineTotal)
        launchSaving {
            receiptRepository.addManualLineItem(
                receiptId,
                NewManualLineItem(
                    rawText = rawText,
                    quantity = validQuantity,
                    unitPriceMinor = validUnitPrice,
                    lineTotalMinor = validLineTotal,
                ),
            )
            "Missing line added. Match it to a product before confirming."
        }
    }

    fun createManualReceipt(
        merchantName: String,
        purchasedAt: String,
        subtotalInput: String,
        taxInput: String,
        totalInput: String,
    ) {
        val subtotal = subtotalInput.toMinorUnits()
        val tax = taxInput.toMinorUnits()
        val total = totalInput.toMinorUnits()
        if (
            merchantName.isBlank() ||
            !purchasedAt.isValidIsoDate() ||
            total == null ||
            (subtotalInput.isNotBlank() && subtotal == null) ||
            (taxInput.isNotBlank() && tax == null)
        ) {
            showMessage("Enter a merchant, valid YYYY-MM-DD date, and valid receipt totals.")
            return
        }
        val validTotal = requireNotNull(total)
        launchSaving {
            val merchantId = catalogRepository.getOrCreateMerchant(merchantName)
            val receiptId = receiptRepository.createManualReceipt(
                NewManualReceipt(
                    merchantId = merchantId,
                    purchasedAt = purchasedAt,
                    subtotalMinor = subtotal,
                    taxMinor = tax,
                    totalMinor = validTotal,
                ),
            )
            selectedReceiptId.value = receiptId
            savedStateHandle["receiptId"] = receiptId
            "Manual receipt created. Add its product lines below."
        }
    }

    fun addManualObservation(
        productId: Long?,
        merchantName: String,
        observedAt: String,
        shelfPriceInput: String,
        packSizeInput: String,
    ) {
        val price = shelfPriceInput.toMinorUnits()
        val packSize = packSizeInput.toReceiptNumber()
        if (
            productId == null ||
            !observedAt.isValidIsoDate() ||
            price == null ||
            (packSize ?: 0.0) <= 0.0
        ) {
            showMessage("Choose a product and enter a valid date, price, and pack size.")
            return
        }
        val validProductId = requireNotNull(productId)
        val validPrice = requireNotNull(price)
        val validPackSize = requireNotNull(packSize)
        launchSaving {
            val merchantId = merchantName.trim().takeIf(String::isNotEmpty)
                ?.let { catalogRepository.getOrCreateMerchant(it) }
            observationRepository.addObservation(
                NewObservation(
                    productId = validProductId,
                    observedAt = observedAt,
                    shelfPriceMinor = validPrice,
                    packSize = validPackSize,
                    merchantId = merchantId,
                ),
            )
            "Manual price observation added."
        }
    }

    fun dismissMessage() {
        transient.update { it.copy(message = null) }
    }

    fun confirmReceipt() {
        val state = uiState.value
        val receiptId = state.receiptId ?: return
        if (!state.confirmEnabled) {
            showMessage("Resolve every included line and reconcile the totals within £0.02.")
            return
        }
        transient.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val merchantId = catalogRepository.getOrCreateMerchant(state.merchantInput)
                receiptRepository.confirmReceipt(
                    receiptId = receiptId,
                    correction = ReceiptCorrection(
                        merchantId = merchantId,
                        purchasedAt = state.purchasedAtInput,
                        subtotalMinor = state.subtotalMinor,
                        taxMinor = state.taxMinor,
                        totalMinor = requireNotNull(state.totalMinor),
                    ),
                    items = state.items.map { item ->
                        ConfirmedLineItem(
                            lineItemId = item.id,
                            productId = item.productId,
                            quantity = item.quantity ?: 1.0,
                            unitPriceMinor = item.unitPriceMinor ?: 0L,
                            lineTotalMinor = item.lineTotalMinor ?: 0L,
                            packSize = item.packSize,
                            matchConfidence = item.confidence,
                            excluded = item.excluded,
                        )
                    },
                )
            }.onSuccess {
                selectedReceiptId.value = null
                savedStateHandle["receiptId"] = null
                transient.value = ReviewTransientState(
                    message = "Receipt confirmed and price observations added.",
                )
            }.onFailure(::handleFailure)
        }
    }

    private fun updateHeader(transform: HeaderEdit.() -> HeaderEdit) {
        val state = uiState.value
        val receiptId = state.receiptId ?: return
        transient.update { draft ->
            val current = draft.header?.takeIf { it.receiptId == receiptId } ?: HeaderEdit(
                receiptId = receiptId,
                merchantInput = state.merchantInput,
                purchasedAtInput = state.purchasedAtInput,
                subtotalInput = state.subtotalInput,
                taxInput = state.taxInput,
                totalInput = state.totalInput,
            )
            draft.copy(header = current.transform(), message = null)
        }
    }

    private fun updateMoneyHeader(
        input: String,
        transform: HeaderEdit.(String) -> HeaderEdit,
    ) {
        if (!input.isMoneyInput()) return
        updateHeader { transform(input) }
    }

    private fun updateNumber(
        lineItemId: Long,
        input: String,
        transform: ReviewEdit.(String) -> ReviewEdit,
    ) {
        if (!input.isReceiptNumberInput()) return
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        updateEdit(item) { transform(input) }
    }

    private fun updateMoney(
        lineItemId: Long,
        input: String,
        transform: ReviewEdit.(String) -> ReviewEdit,
    ) {
        if (!input.isMoneyInput()) return
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        updateEdit(item) { transform(input) }
    }

    private fun updateSignedMoney(
        lineItemId: Long,
        input: String,
        transform: ReviewEdit.(String) -> ReviewEdit,
    ) {
        if (!input.isSignedMoneyInput()) return
        val item = uiState.value.items.firstOrNull { it.id == lineItemId } ?: return
        updateEdit(item) { transform(input) }
    }

    private fun updateEdit(
        item: ReviewLineItemUi,
        transform: ReviewEdit.() -> ReviewEdit,
    ) {
        transient.update { state ->
            val current = state.edits[item.id] ?: ReviewEdit(
                productId = item.productId,
                quantityInput = item.quantityInput,
                unitPriceInput = item.unitPriceInput,
                lineTotalInput = item.lineTotalInput,
                packSizeInput = item.packSizeInput,
                wasUserMatched = item.wasUserMatched,
                excluded = item.excluded,
            )
            state.copy(
                edits = state.edits + (item.id to current.transform()),
                message = null,
            )
        }
    }

    private fun launchSaving(block: suspend () -> String) {
        if (transient.value.isSaving) return
        transient.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { message ->
                    transient.update { it.copy(isSaving = false, message = message) }
                }
                .onFailure(::handleFailure)
        }
    }

    private fun handleFailure(error: Throwable) {
        if (error is CancellationException) throw error
        transient.update {
            it.copy(
                isSaving = false,
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "The change could not be saved. Check the fields and try again.",
            )
        }
    }

    private fun showMessage(message: String) {
        transient.update { it.copy(message = message) }
    }
}

internal fun String.toReceiptNumber(): Double? =
    replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

internal fun String.toMinorUnits(): Long? {
    val normalized = trim().replace(',', '.')
    if (normalized.isEmpty() || !normalized.matches(Regex("""\d{1,7}(?:\.\d{0,2})?"""))) {
        return null
    }
    val parts = normalized.split('.', limit = 2)
    val major = parts[0].toLongOrNull() ?: return null
    val minor = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    return major * 100L + minor
}

internal fun String.toSignedMinorUnits(): Long? {
    val normalized = trim().replace(',', '.')
    val sign = if (normalized.startsWith('-')) -1L else 1L
    val magnitude = normalized.removePrefix("-").toMinorUnits() ?: return null
    return magnitude * sign
}

internal fun String.isValidIsoDate(): Boolean {
    if (!matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return false
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    return formatter.parse(this, position) != null && position.index == length
}

private fun String.isReceiptNumberInput(): Boolean =
    isEmpty() || matches(Regex("""\d{0,7}(?:[.,]\d{0,3})?"""))

private fun String.isMoneyInput(): Boolean =
    isEmpty() || matches(Regex("""\d{0,7}(?:[.,]\d{0,2})?"""))

private fun String.isSignedMoneyInput(): Boolean =
    isEmpty() || matches(Regex("""-?\d{0,7}(?:[.,]\d{0,2})?"""))

private fun Double.toInputString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Long.toMoneyInput(): String =
    "${this / 100}.${(this % 100).toString().padStart(2, '0')}"
