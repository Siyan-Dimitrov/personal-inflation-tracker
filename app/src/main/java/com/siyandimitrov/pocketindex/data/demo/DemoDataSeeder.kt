package com.siyandimitrov.pocketindex.data.demo

import androidx.room.withTransaction
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.LineItemEntity
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import com.siyandimitrov.pocketindex.data.local.RecurringItemEntity
import com.siyandimitrov.pocketindex.data.local.UnitType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opt-in sample data for previews, screenshots, or manual QA.
 *
 * Nothing calls this automatically. A debug-only UI or instrumentation setup can inject it and
 * invoke [seedIfEmpty], without allowing BuildConfig concerns into the data layer.
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val database: PocketIndexDatabase,
) {
    suspend fun seedIfEmpty(): Boolean = database.withTransaction {
        val productDao = database.productDao()
        val receiptDao = database.receiptDao()
        if (productDao.count() > 0 || receiptDao.count() > 0) return@withTransaction false

        val categoryDao = database.categoryDao()
        val merchantDao = database.merchantDao()
        val observationDao = database.priceObservationDao()
        val recurringDao = database.recurringItemDao()

        val groceriesId = categoryDao.insert(CategoryEntity(name = "Groceries"))
        val householdId = categoryDao.insert(CategoryEntity(name = "Household bills"))
        check(groceriesId > 0 && householdId > 0)

        val marketId = merchantDao.insert(MerchantEntity(name = "Neighbourhood Market"))
        check(marketId > 0)

        val milkId = productDao.insert(
            ProductEntity(
                canonicalName = "Whole milk",
                categoryId = groceriesId,
                unitType = UnitType.VOLUME_ML,
                packSize = 1_136.0,
                aliases = """["WHOLE MILK 2PT"]""",
            ),
        )
        val breadId = productDao.insert(
            ProductEntity(
                canonicalName = "Wholemeal bread",
                categoryId = groceriesId,
                unitType = UnitType.COUNT,
                packSize = 1.0,
                aliases = """["WHOLEMEAL LOAF"]""",
            ),
        )
        val broadbandId = productDao.insert(
            ProductEntity(
                canonicalName = "Broadband",
                categoryId = householdId,
                unitType = UnitType.SERVICE,
                packSize = 1.0,
            ),
        )
        val electricityId = productDao.insert(
            ProductEntity(
                canonicalName = "Electricity",
                categoryId = householdId,
                unitType = UnitType.SERVICE,
                packSize = 1.0,
            ),
        )
        val councilTaxId = productDao.insert(
            ProductEntity(
                canonicalName = "Council tax",
                categoryId = householdId,
                unitType = UnitType.SERVICE,
                packSize = 1.0,
            ),
        )
        check(
            milkId > 0 &&
                breadId > 0 &&
                broadbandId > 0 &&
                electricityId > 0 &&
                councilTaxId > 0,
        )

        val receiptId = receiptDao.insert(
            ReceiptEntity(
                merchantId = marketId,
                purchasedAt = "2026-07-26",
                totalMinor = 320,
                subtotalMinor = 320,
                taxMinor = 0,
                imagePath = "demo/receipt-2026-07-26.jpg",
                ocrText = "WHOLE MILK 2PT 1.70\nWHOLEMEAL LOAF 1.50\nTOTAL 3.20",
                status = ReceiptStatus.CONFIRMED,
                createdAt = 1_722_009_600_000,
            ),
        )
        val lineIds = receiptDao.insertLineItems(
            listOf(
                LineItemEntity(
                    receiptId = receiptId,
                    rawText = "WHOLE MILK 2PT",
                    quantity = 1.0,
                    unitPriceMinor = 170,
                    lineTotalMinor = 170,
                    productId = milkId,
                    matchConfidence = 0.98,
                    userConfirmed = true,
                ),
                LineItemEntity(
                    receiptId = receiptId,
                    rawText = "WHOLEMEAL LOAF",
                    quantity = 1.0,
                    unitPriceMinor = 150,
                    lineTotalMinor = 150,
                    productId = breadId,
                    matchConfidence = 0.96,
                    userConfirmed = true,
                ),
            ),
        )
        observationDao.insertAll(
            listOf(
                PriceObservationEntity(
                    productId = milkId,
                    observedAt = "2026-07-26",
                    unitPriceMicros = ((170.0 / 1_136.0) * 1_000_000).toLong(),
                    shelfPriceMinor = 170,
                    packSize = 1_136.0,
                    merchantId = marketId,
                    source = ObservationSource.RECEIPT,
                    receiptLineItemId = lineIds[0],
                ),
                PriceObservationEntity(
                    productId = breadId,
                    observedAt = "2026-07-26",
                    unitPriceMicros = 150_000_000,
                    shelfPriceMinor = 150,
                    packSize = 1.0,
                    merchantId = marketId,
                    source = ObservationSource.RECEIPT,
                    receiptLineItemId = lineIds[1],
                ),
                PriceObservationEntity(
                    productId = broadbandId,
                    observedAt = "2026-07-01",
                    unitPriceMicros = 3_200_000_000,
                    shelfPriceMinor = 3_200,
                    packSize = 1.0,
                    source = ObservationSource.BILL,
                ),
                PriceObservationEntity(
                    productId = electricityId,
                    observedAt = "2026-07-01",
                    unitPriceMicros = 9_200_000_000,
                    shelfPriceMinor = 9_200,
                    packSize = 1.0,
                    source = ObservationSource.BILL,
                ),
                PriceObservationEntity(
                    productId = councilTaxId,
                    observedAt = "2026-07-01",
                    unitPriceMicros = 16_200_000_000,
                    shelfPriceMinor = 16_200,
                    packSize = 1.0,
                    source = ObservationSource.BILL,
                ),
            ),
        )
        recurringDao.insert(
            RecurringItemEntity(
                productId = broadbandId,
                cadence = RecurringCadence.MONTHLY,
                currentPriceMinor = 3_200,
                lastUpdated = "2026-07-01",
            ),
        )
        recurringDao.insert(
            RecurringItemEntity(
                productId = electricityId,
                cadence = RecurringCadence.MONTHLY,
                currentPriceMinor = 9_200,
                lastUpdated = "2026-07-01",
            ),
        )
        recurringDao.insert(
            RecurringItemEntity(
                productId = councilTaxId,
                cadence = RecurringCadence.MONTHLY,
                currentPriceMinor = 16_200,
                lastUpdated = "2026-07-01",
            ),
        )
        true
    }
}
