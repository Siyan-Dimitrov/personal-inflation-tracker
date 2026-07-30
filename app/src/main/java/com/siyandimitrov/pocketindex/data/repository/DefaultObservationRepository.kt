package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.PriceObservationDao
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultObservationRepository @Inject constructor(
    private val observations: PriceObservationDao,
) : ObservationRepository {
    override fun observeProductTimeline(productId: Long) =
        observations.observeForProduct(productId)

    override fun observeCountsByProduct() = observations.observeCountsByProduct()

    override suspend fun addObservation(observation: NewObservation): Long =
        observations.insert(
            PriceObservationEntity(
                productId = observation.productId,
                observedAt = observation.observedAt,
                unitPriceMicros = unitPriceMicros(
                    observation.shelfPriceMinor,
                    observation.packSize,
                ),
                shelfPriceMinor = observation.shelfPriceMinor,
                packSize = observation.packSize,
                merchantId = observation.merchantId,
                source = observation.source,
            ),
        )

    override suspend fun observationsBetween(
        startDate: String,
        endDate: String,
    ) = observations.getBetween(startDate, endDate)
}
