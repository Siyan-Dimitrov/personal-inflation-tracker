package com.siyandimitrov.pocketindex.data.repository

import androidx.room.withTransaction
import com.siyandimitrov.pocketindex.data.local.LineItemEntity
import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DefaultReceiptRepository @Inject constructor(
    private val database: PocketIndexDatabase,
) : ReceiptRepository {
    private val receipts = database.receiptDao()
    private val observations = database.priceObservationDao()
    private val products = database.productDao()

    override fun observeReceipts() = receipts.observeReceiptList()

    override fun observeReviewQueue() = receipts.observeReviewQueue()

    override fun observeReceipt(receiptId: Long) = receipts.observeWithDetails(receiptId)

    override fun observeConfirmedSpendByCategory() = receipts.observeConfirmedSpendByCategory()

    override suspend fun createPendingReceipt(receipt: NewReceipt): Long =
        receipts.insert(
            ReceiptEntity(
                imagePath = receipt.imagePath,
                purchasedAt = receipt.purchasedAt,
                totalMinor = receipt.totalMinor,
                currency = receipt.currency,
            ),
        )

    override suspend fun createManualReceipt(receipt: NewManualReceipt): Long {
        validateReceiptCorrection(
            ReceiptCorrection(
                merchantId = receipt.merchantId,
                purchasedAt = receipt.purchasedAt,
                subtotalMinor = receipt.subtotalMinor,
                taxMinor = receipt.taxMinor,
                totalMinor = receipt.totalMinor,
            ),
        )
        return receipts.insert(
            ReceiptEntity(
                merchantId = receipt.merchantId,
                purchasedAt = receipt.purchasedAt,
                totalMinor = receipt.totalMinor,
                subtotalMinor = receipt.subtotalMinor,
                taxMinor = receipt.taxMinor,
                currency = receipt.currency,
                imagePath = "",
                status = ReceiptStatus.NEEDS_REVIEW,
            ),
        )
    }

    override suspend fun addManualLineItem(
        receiptId: Long,
        item: NewManualLineItem,
    ): Long = database.withTransaction {
        val receipt = checkNotNull(receipts.getById(receiptId)) {
            "Receipt $receiptId does not exist."
        }
        require(receipt.status != ReceiptStatus.CONFIRMED) {
            "A confirmed receipt cannot be changed."
        }
        val evidence = item.rawText.trim().take(240)
        require(evidence.isNotEmpty()) { "A line description is required." }
        require(item.quantity > 0.0 && item.quantity.isFinite()) {
            "Quantity must be greater than zero."
        }
        require(
            item.productId == null ||
                (item.unitPriceMinor >= 0 && item.lineTotalMinor >= 0),
        ) {
            "Prices cannot be negative."
        }
        item.productId?.let { productId ->
            checkNotNull(products.getById(productId)) { "Product $productId does not exist." }
        }
        receipts.insertLineItem(
            LineItemEntity(
                receiptId = receiptId,
                rawText = evidence,
                quantity = item.quantity,
                unitPriceMinor = item.unitPriceMinor,
                lineTotalMinor = item.lineTotalMinor,
                productId = item.productId,
                matchConfidence = item.productId?.let { 1.0 },
            ),
        )
    }

    override suspend fun saveExtraction(receiptId: Long, extraction: ReceiptExtraction) {
        require(extraction.status != ReceiptStatus.CONFIRMED) {
            "Extraction must be reviewed before confirmation."
        }
        database.withTransaction {
            checkNotNull(receipts.getById(receiptId)) { "Receipt $receiptId does not exist." }
            receipts.applyExtraction(
                receiptId = receiptId,
                merchantId = extraction.merchantId,
                purchasedAt = extraction.purchasedAt,
                subtotalMinor = extraction.subtotalMinor,
                taxMinor = extraction.taxMinor,
                totalMinor = extraction.totalMinor,
                ocrText = extraction.ocrText,
                status = extraction.status,
            )
            receipts.deleteUnconfirmedLineItems(receiptId)
            receipts.insertLineItems(
                extraction.lineItems.map { item ->
                    LineItemEntity(
                        receiptId = receiptId,
                        rawText = item.rawText,
                        quantity = item.quantity,
                        unitPriceMinor = item.unitPriceMinor,
                        lineTotalMinor = item.lineTotalMinor,
                        productId = item.productId,
                        matchConfidence = item.matchConfidence,
                    )
                },
            )
        }
    }

    override suspend fun confirmReceipt(
        receiptId: Long,
        correction: ReceiptCorrection,
        items: List<ConfirmedLineItem>,
    ) {
        require(items.isNotEmpty()) { "At least one line item is required." }
        validateReceiptCorrection(correction)
        database.withTransaction {
            val receipt = checkNotNull(receipts.getById(receiptId)) {
                "Receipt $receiptId does not exist."
            }
            require(receipt.status != ReceiptStatus.CONFIRMED) {
                "This receipt has already been confirmed."
            }
            val receiptDetails = checkNotNull(receipts.getWithDetails(receiptId))
            val existingLineIds = receiptDetails.lineItems.mapTo(mutableSetOf()) { it.id }
            val rawTextByLineId = receiptDetails.lineItems.associate { it.id to it.rawText }
            require(items.map { it.lineItemId }.toSet().size == items.size) {
                "A line item can only be confirmed once."
            }
            require(items.mapTo(mutableSetOf()) { it.lineItemId } == existingLineIds) {
                "Every receipt line must be matched or explicitly excluded."
            }
            require(items.any { !it.excluded }) { "At least one product line is required." }
            // Evidence-only lines still belong to the receipt arithmetic. For example, a
            // refundable deposit or discount can be excluded from inflation observations while
            // its signed amount remains necessary to reproduce the printed subtotal.
            val reviewedSubtotal = items.sumOf(ConfirmedLineItem::lineTotalMinor)
            require(
                receiptReconciles(
                    reviewedSubtotal = reviewedSubtotal,
                    subtotalMinor = correction.subtotalMinor,
                    taxMinor = correction.taxMinor,
                    totalMinor = correction.totalMinor,
                ),
            ) {
                "Product lines, subtotal, tax, and total must reconcile within £0.02."
            }

            receipts.updateCorrectableDetails(
                receiptId = receiptId,
                merchantId = correction.merchantId,
                purchasedAt = correction.purchasedAt,
                subtotalMinor = correction.subtotalMinor,
                taxMinor = correction.taxMinor,
                totalMinor = correction.totalMinor,
            )
            items.forEach { item ->
                require(item.quantity > 0.0 && item.quantity.isFinite()) {
                    "Quantity must be greater than zero."
                }
                if (!item.excluded) {
                    requireNotNull(item.productId) { "Choose a product for every included line." }
                    require((item.packSize ?: 0.0) > 0.0 && item.packSize?.isFinite() == true) {
                        "Pack size must be greater than zero."
                    }
                    require(item.unitPriceMinor >= 0 && item.lineTotalMinor >= 0) {
                        "Product prices cannot be negative."
                    }
                }
                receipts.updateLineItemMatch(
                    lineItemId = item.lineItemId,
                    productId = item.productId.takeUnless { item.excluded },
                    quantity = item.quantity,
                    unitPriceMinor = item.unitPriceMinor,
                    lineTotalMinor = item.lineTotalMinor,
                    matchConfidence = item.matchConfidence,
                    userConfirmed = true,
                )
                observations.deleteForLineItem(item.lineItemId)
                if (item.excluded) return@forEach
                val productId = requireNotNull(item.productId)
                val packSize = requireNotNull(item.packSize)
                observations.insert(
                    PriceObservationEntity(
                        productId = productId,
                        observedAt = correction.purchasedAt,
                        unitPriceMicros = unitPriceMicros(item.unitPriceMinor, packSize),
                        shelfPriceMinor = item.unitPriceMinor,
                        packSize = packSize,
                        merchantId = correction.merchantId,
                        source = ObservationSource.RECEIPT,
                        receiptLineItemId = item.lineItemId,
                    ),
                )
                val rawText = rawTextByLineId.getValue(item.lineItemId)
                val product = checkNotNull(products.getById(productId)) {
                    "Product $productId does not exist."
                }
                products.updateAliases(
                    productId = product.id,
                    aliases = appendJsonAlias(product.aliases, rawText),
                )
            }
            receipts.updateStatus(receiptId, ReceiptStatus.CONFIRMED)
        }
    }

    override suspend fun deleteReceipt(receiptId: Long): String? = database.withTransaction {
        val receipt = receipts.getById(receiptId) ?: return@withTransaction null
        // Deleting a line item only nulls the observation's reference to it, so the
        // observations must go explicitly or an accidental scan keeps feeding the index.
        observations.deleteForReceipt(receiptId)
        receipts.deleteById(receiptId)
        receipt.imagePath
    }

    private companion object {
        const val RECONCILIATION_TOLERANCE_MINOR = 2L

        fun appendJsonAlias(existingJson: String, alias: String): String {
            if (alias.isBlank()) return existingJson
            val escaped = buildString(alias.length + 2) {
                append('"')
                alias.forEach { character ->
                    when (character) {
                        '"' -> append("\\\"")
                        '\\' -> append("\\\\")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> {
                            if (character.code < 0x20) {
                                append("\\u")
                                append(character.code.toString(16).padStart(4, '0'))
                            } else {
                                append(character)
                            }
                        }
                    }
                }
                append('"')
            }
            val json = existingJson.trim()
            if (json == "[]") return "[$escaped]"
            if (json.startsWith("[") && json.endsWith("]")) {
                if (json.contains(escaped, ignoreCase = true)) return existingJson
                return "${json.dropLast(1)},$escaped]"
            }
            return "[$escaped]"
        }
    }
}

internal fun receiptReconciles(
    reviewedSubtotal: Long,
    subtotalMinor: Long?,
    taxMinor: Long?,
    totalMinor: Long,
    toleranceMinor: Long = 2L,
): Boolean {
    val expectedSubtotal = subtotalMinor ?: (totalMinor - (taxMinor ?: 0L))
    if (abs(reviewedSubtotal - expectedSubtotal) > toleranceMinor) return false
    return taxMinor == null || abs(expectedSubtotal + taxMinor - totalMinor) <= toleranceMinor
}

private fun validateReceiptCorrection(correction: ReceiptCorrection) {
    require(correction.purchasedAt.isStrictIsoDate()) {
        "Use a purchase date in YYYY-MM-DD format."
    }
    require(correction.totalMinor >= 0) { "Total cannot be negative." }
    require(correction.subtotalMinor == null || correction.subtotalMinor >= 0) {
        "Subtotal cannot be negative."
    }
    require(correction.taxMinor == null || correction.taxMinor >= 0) {
        "Tax cannot be negative."
    }
}

private fun String.isStrictIsoDate(): Boolean {
    if (!matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return false
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    return formatter.parse(this, position) != null && position.index == length
}
