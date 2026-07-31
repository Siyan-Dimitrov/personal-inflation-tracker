package com.siyandimitrov.pocketindex.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded

/**
 * Analytical observation enriched with the receipt quantity omitted from price_observations.
 * Manual observations and bills have no source line and therefore expose a null receipt quantity.
 */
data class AnalyticalPriceObservation(
    @Embedded
    val observation: PriceObservationEntity,
    @ColumnInfo(name = "receipt_quantity")
    val receiptQuantity: Double?,
)
