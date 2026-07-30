package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import kotlin.math.roundToLong

data class NewReceipt(
    val imagePath: String,
    val purchasedAt: String,
    val totalMinor: Long = 0,
    val currency: String = "GBP",
)

data class ExtractedLineItem(
    val rawText: String,
    val quantity: Double = 1.0,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val productId: Long? = null,
    val matchConfidence: Double? = null,
)

data class ReceiptExtraction(
    val merchantId: Long?,
    val subtotalMinor: Long?,
    val taxMinor: Long?,
    val totalMinor: Long,
    val ocrText: String,
    val status: ReceiptStatus,
    val lineItems: List<ExtractedLineItem>,
)

data class ConfirmedLineItem(
    val lineItemId: Long,
    val productId: Long,
    val quantity: Double,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val packSize: Double,
    val matchConfidence: Double? = null,
)

data class NewObservation(
    val productId: Long,
    val observedAt: String,
    val shelfPriceMinor: Long,
    val packSize: Double,
    val merchantId: Long? = null,
    val source: ObservationSource = ObservationSource.MANUAL,
)

internal fun unitPriceMicros(shelfPriceMinor: Long, packSize: Double): Long {
    require(packSize > 0.0) { "Pack size must be greater than zero." }
    return ((shelfPriceMinor.toDouble() / packSize) * 1_000_000.0).roundToLong()
}
