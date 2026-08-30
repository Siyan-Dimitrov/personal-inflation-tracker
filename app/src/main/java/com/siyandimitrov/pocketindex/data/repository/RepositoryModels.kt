package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import com.siyandimitrov.pocketindex.data.local.UnitType
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
    /** Transaction date read from the receipt; null keeps the date recorded at scan time. */
    val purchasedAt: String? = null,
    val subtotalMinor: Long?,
    val taxMinor: Long?,
    val totalMinor: Long,
    val ocrText: String,
    val status: ReceiptStatus,
    val lineItems: List<ExtractedLineItem>,
)

data class ConfirmedLineItem(
    val lineItemId: Long,
    val productId: Long?,
    val quantity: Double,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val packSize: Double?,
    val matchConfidence: Double? = null,
    val excluded: Boolean = false,
)

/**
 * Correctable receipt fields. Image and OCR evidence are intentionally absent.
 */
data class ReceiptCorrection(
    val merchantId: Long?,
    val purchasedAt: String,
    val subtotalMinor: Long?,
    val taxMinor: Long?,
    val totalMinor: Long,
)

data class NewManualReceipt(
    val merchantId: Long?,
    val purchasedAt: String,
    val subtotalMinor: Long?,
    val taxMinor: Long?,
    val totalMinor: Long,
    val currency: String = "GBP",
)

data class NewManualLineItem(
    val rawText: String,
    val quantity: Double = 1.0,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val productId: Long? = null,
)

data class NewCatalogProduct(
    val canonicalName: String,
    val categoryId: Long,
    val unitType: UnitType,
    val packSize: Double?,
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

/**
 * Months covered by one payment of a recurring bill. Stored as the bill observation's pack
 * size, so its unit price is always per month and a change of cadence is not read as inflation.
 */
internal fun RecurringCadence.monthsPerPeriod(): Double = when (this) {
    RecurringCadence.WEEKLY -> 12.0 / 52.0
    RecurringCadence.MONTHLY -> 1.0
    RecurringCadence.QUARTERLY -> 3.0
    RecurringCadence.ANNUAL -> 12.0
}
