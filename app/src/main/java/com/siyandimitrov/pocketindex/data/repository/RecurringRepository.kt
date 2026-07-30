package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import com.siyandimitrov.pocketindex.data.local.RecurringItemWithProduct
import kotlinx.coroutines.flow.Flow

interface RecurringRepository {
    fun observeAll(): Flow<List<RecurringItemWithProduct>>
    fun observeActive(): Flow<List<RecurringItemWithProduct>>
    suspend fun recordPrice(
        productId: Long,
        cadence: RecurringCadence,
        priceMinor: Long,
        observedAt: String,
    ): Long
    suspend fun setActive(recurringItemId: Long, active: Boolean)
}
