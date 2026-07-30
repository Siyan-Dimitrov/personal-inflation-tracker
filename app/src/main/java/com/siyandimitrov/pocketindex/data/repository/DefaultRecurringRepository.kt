package com.siyandimitrov.pocketindex.data.repository

import androidx.room.withTransaction
import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import com.siyandimitrov.pocketindex.data.local.RecurringItemEntity
import com.siyandimitrov.pocketindex.data.local.UnitType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultRecurringRepository @Inject constructor(
    private val database: PocketIndexDatabase,
) : RecurringRepository {
    private val recurringItems = database.recurringItemDao()
    private val products = database.productDao()
    private val observations = database.priceObservationDao()

    override fun observeAll() = recurringItems.observeAllWithProduct()
    override fun observeActive() = recurringItems.observeActiveWithProduct()

    override suspend fun recordPrice(
        productId: Long,
        cadence: RecurringCadence,
        priceMinor: Long,
        observedAt: String,
    ): Long {
        require(priceMinor >= 0) { "Price cannot be negative." }
        return database.withTransaction {
            val product = checkNotNull(products.getById(productId)) {
                "Product $productId does not exist."
            }
            require(product.unitType == UnitType.SERVICE) {
                "Recurring costs must use a SERVICE product."
            }
            val existing = recurringItems.getByProductId(productId)
            val recurringId = if (existing == null) {
                recurringItems.insert(
                    RecurringItemEntity(
                        productId = productId,
                        cadence = cadence,
                        currentPriceMinor = priceMinor,
                        lastUpdated = observedAt,
                    ),
                )
            } else {
                recurringItems.update(
                    existing.copy(
                        cadence = cadence,
                        currentPriceMinor = priceMinor,
                        lastUpdated = observedAt,
                        active = true,
                    ),
                )
                existing.id
            }
            observations.insert(
                PriceObservationEntity(
                    productId = productId,
                    observedAt = observedAt,
                    unitPriceMicros = unitPriceMicros(priceMinor, 1.0),
                    shelfPriceMinor = priceMinor,
                    packSize = 1.0,
                    source = ObservationSource.BILL,
                ),
            )
            recurringId
        }
    }

    override suspend fun setActive(recurringItemId: Long, active: Boolean) =
        recurringItems.setActive(recurringItemId, active)
}
