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
        require(observedAt.isNotBlank()) { "Observation date cannot be blank." }
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
                check(
                    recurringItems.update(
                        existing.copy(
                            cadence = cadence,
                            currentPriceMinor = priceMinor,
                            lastUpdated = observedAt,
                            active = true,
                        ),
                    ) == 1,
                ) {
                    "Recurring bill ${existing.id} could not be updated."
                }
                existing.id
            }
            observations.insert(
                billObservation(
                    productId = productId,
                    priceMinor = priceMinor,
                    observedAt = observedAt,
                ),
            )
            recurringId
        }
    }

    override suspend fun updateRecurringBill(
        recurringItemId: Long,
        name: String,
        cadence: RecurringCadence,
        priceMinor: Long,
        observedAt: String,
    ) {
        val canonicalName = name.trim()
        require(canonicalName.isNotEmpty()) { "Bill name cannot be blank." }
        require(priceMinor >= 0) { "Price cannot be negative." }
        require(observedAt.isNotBlank()) { "Observation date cannot be blank." }

        database.withTransaction {
            val recurringItem = checkNotNull(recurringItems.getById(recurringItemId)) {
                "Recurring bill $recurringItemId does not exist."
            }
            require(recurringItem.active) {
                "Recurring bill $recurringItemId is inactive; restore it before editing."
            }
            val product = checkNotNull(products.getById(recurringItem.productId)) {
                "Product ${recurringItem.productId} does not exist."
            }
            require(product.unitType == UnitType.SERVICE) {
                "Recurring costs must use a SERVICE product."
            }
            val nameOwner = products.findByCanonicalName(canonicalName)
            require(nameOwner == null || nameOwner.id == product.id) {
                "A product named \"$canonicalName\" already exists."
            }

            if (product.canonicalName != canonicalName) {
                products.update(product.copy(canonicalName = canonicalName))
            }
            check(
                recurringItems.update(
                    recurringItem.copy(
                        cadence = cadence,
                        currentPriceMinor = priceMinor,
                        lastUpdated = observedAt,
                    ),
                ) == 1,
            ) {
                "Recurring bill $recurringItemId could not be updated."
            }
            if (priceMinor != recurringItem.currentPriceMinor) {
                observations.insert(
                    billObservation(
                        productId = product.id,
                        priceMinor = priceMinor,
                        observedAt = observedAt,
                    ),
                )
            }
        }
    }

    override suspend fun removeRecurringBill(recurringItemId: Long) {
        setActive(recurringItemId, active = false)
    }

    override suspend fun setActive(recurringItemId: Long, active: Boolean) {
        database.withTransaction {
            checkNotNull(recurringItems.getById(recurringItemId)) {
                "Recurring bill $recurringItemId does not exist."
            }
            check(recurringItems.setActive(recurringItemId, active) == 1) {
                "Recurring bill $recurringItemId could not be updated."
            }
        }
    }

    private fun billObservation(
        productId: Long,
        priceMinor: Long,
        observedAt: String,
    ) = PriceObservationEntity(
        productId = productId,
        observedAt = observedAt,
        unitPriceMicros = unitPriceMicros(priceMinor, 1.0),
        shelfPriceMinor = priceMinor,
        packSize = 1.0,
        source = ObservationSource.BILL,
    )
}
