package com.siyandimitrov.pocketindex.data.repository

import androidx.room.withTransaction
import com.siyandimitrov.pocketindex.data.local.LineItemEntity
import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
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

    override suspend fun saveExtraction(receiptId: Long, extraction: ReceiptExtraction) {
        require(extraction.status != ReceiptStatus.CONFIRMED) {
            "Extraction must be reviewed before confirmation."
        }
        database.withTransaction {
            checkNotNull(receipts.getById(receiptId)) { "Receipt $receiptId does not exist." }
            receipts.applyExtraction(
                receiptId = receiptId,
                merchantId = extraction.merchantId,
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

    override suspend fun confirmReceipt(receiptId: Long, items: List<ConfirmedLineItem>) {
        require(items.isNotEmpty()) { "At least one line item is required." }
        database.withTransaction {
            val receipt = checkNotNull(receipts.getById(receiptId)) {
                "Receipt $receiptId does not exist."
            }
            val receiptDetails = checkNotNull(receipts.getWithDetails(receiptId))
            val existingLineIds = receiptDetails.lineItems.mapTo(mutableSetOf()) { it.id }
            val rawTextByLineId = receiptDetails.lineItems.associate { it.id to it.rawText }
            require(items.map { it.lineItemId }.toSet().size == items.size) {
                "A line item can only be confirmed once."
            }
            require(items.all { it.lineItemId in existingLineIds }) {
                "Every confirmation must belong to receipt $receiptId."
            }
            val replacements = items.associateBy { it.lineItemId }
            val reviewedSubtotal = checkNotNull(receipts.getWithDetails(receiptId))
                .lineItems
                .sumOf { replacements[it.id]?.lineTotalMinor ?: it.lineTotalMinor }
            val expectedSubtotal = receipt.subtotalMinor
                ?: (receipt.totalMinor - (receipt.taxMinor ?: 0))
            require(abs(reviewedSubtotal - expectedSubtotal) <= RECONCILIATION_TOLERANCE_MINOR) {
                "Line items differ from the receipt subtotal by more than 2 minor units."
            }
            receipt.taxMinor?.let { tax ->
                require(abs(expectedSubtotal + tax - receipt.totalMinor) <= RECONCILIATION_TOLERANCE_MINOR) {
                    "Subtotal and tax do not reconcile with the receipt total."
                }
            }

            items.forEach { item ->
                require(item.quantity > 0.0) { "Quantity must be greater than zero." }
                require(item.packSize > 0.0) { "Pack size must be greater than zero." }
                require(item.unitPriceMinor >= 0 && item.lineTotalMinor >= 0) {
                    "Prices cannot be negative."
                }
                receipts.updateLineItemMatch(
                    lineItemId = item.lineItemId,
                    productId = item.productId,
                    quantity = item.quantity,
                    unitPriceMinor = item.unitPriceMinor,
                    lineTotalMinor = item.lineTotalMinor,
                    matchConfidence = item.matchConfidence,
                    userConfirmed = true,
                )
                observations.deleteForLineItem(item.lineItemId)
                observations.insert(
                    PriceObservationEntity(
                        productId = item.productId,
                        observedAt = receipt.purchasedAt,
                        unitPriceMicros = unitPriceMicros(item.unitPriceMinor, item.packSize),
                        shelfPriceMinor = item.unitPriceMinor,
                        packSize = item.packSize,
                        merchantId = receipt.merchantId,
                        source = ObservationSource.RECEIPT,
                        receiptLineItemId = item.lineItemId,
                    ),
                )
                val rawText = rawTextByLineId.getValue(item.lineItemId)
                val product = checkNotNull(products.getById(item.productId)) {
                    "Product ${item.productId} does not exist."
                }
                products.updateAliases(
                    productId = product.id,
                    aliases = appendJsonAlias(product.aliases, rawText),
                )
            }
            receipts.updateStatus(receiptId, ReceiptStatus.CONFIRMED)
        }
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
