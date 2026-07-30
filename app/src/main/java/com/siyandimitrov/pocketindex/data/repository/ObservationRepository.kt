package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.ProductObservationCount
import kotlinx.coroutines.flow.Flow

interface ObservationRepository {
    fun observeProductTimeline(productId: Long): Flow<List<PriceObservationEntity>>
    fun observeCountsByProduct(): Flow<List<ProductObservationCount>>
    suspend fun addObservation(observation: NewObservation): Long
    suspend fun observationsBetween(
        startDate: String,
        endDate: String,
    ): List<PriceObservationEntity>
}
