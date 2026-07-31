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
import kotlin.math.roundToLong

/**
 * Sample data for previews, screenshots, and manual QA. Debug builds invoke [seedIfEmpty] at
 * startup; release builds never call this seeder.
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
            buildList {
                addAll(
                    groceryHistory(
                        productId = milkId,
                        merchantId = marketId,
                        packSize = 1_136.0,
                        prices = MONTHLY_HISTORY.zip(MILK_PRICES_MINOR),
                    ),
                )
                addAll(
                    groceryHistory(
                        productId = breadId,
                        merchantId = marketId,
                        packSize = 1.0,
                        prices = MONTHLY_HISTORY.zip(BREAD_PRICES_MINOR),
                    ),
                )
                addAll(
                    billHistory(
                        productId = broadbandId,
                        prices = MONTHLY_HISTORY.zip(BROADBAND_PRICES_MINOR),
                    ),
                )
                addAll(
                    billHistory(
                        productId = electricityId,
                        prices = MONTHLY_HISTORY.zip(ELECTRICITY_PRICES_MINOR),
                    ),
                )
                addAll(
                    billHistory(
                        productId = councilTaxId,
                        prices = MONTHLY_HISTORY.zip(COUNCIL_TAX_PRICES_MINOR),
                    ),
                )
                addAll(
                    listOf(
                        PriceObservationEntity(
                            productId = milkId,
                            observedAt = "2026-07-26",
                            unitPriceMicros = unitPriceMicros(170L, 1_136.0),
                            shelfPriceMinor = 170,
                            packSize = 1_136.0,
                            merchantId = marketId,
                            source = ObservationSource.RECEIPT,
                            receiptLineItemId = lineIds[0],
                        ),
                        PriceObservationEntity(
                            productId = breadId,
                            observedAt = "2026-07-26",
                            unitPriceMicros = unitPriceMicros(150L, 1.0),
                            shelfPriceMinor = 150,
                            packSize = 1.0,
                            merchantId = marketId,
                            source = ObservationSource.RECEIPT,
                            receiptLineItemId = lineIds[1],
                        ),
                        PriceObservationEntity(
                            productId = broadbandId,
                            observedAt = "2026-07-01",
                            unitPriceMicros = unitPriceMicros(3_200L, 1.0),
                            shelfPriceMinor = 3_200,
                            packSize = 1.0,
                            source = ObservationSource.BILL,
                        ),
                        PriceObservationEntity(
                            productId = electricityId,
                            observedAt = "2026-07-01",
                            unitPriceMicros = unitPriceMicros(9_200L, 1.0),
                            shelfPriceMinor = 9_200,
                            packSize = 1.0,
                            source = ObservationSource.BILL,
                        ),
                        PriceObservationEntity(
                            productId = councilTaxId,
                            observedAt = "2026-07-01",
                            unitPriceMicros = unitPriceMicros(16_200L, 1.0),
                            shelfPriceMinor = 16_200,
                            packSize = 1.0,
                            source = ObservationSource.BILL,
                        ),
                    ),
                )
            },
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

    private fun groceryHistory(
        productId: Long,
        merchantId: Long,
        packSize: Double,
        prices: List<Pair<String, Long>>,
    ): List<PriceObservationEntity> = prices.map { (observedAt, shelfPriceMinor) ->
        PriceObservationEntity(
            productId = productId,
            observedAt = observedAt,
            unitPriceMicros = unitPriceMicros(shelfPriceMinor, packSize),
            shelfPriceMinor = shelfPriceMinor,
            packSize = packSize,
            merchantId = merchantId,
            source = ObservationSource.MANUAL,
        )
    }

    private fun billHistory(
        productId: Long,
        prices: List<Pair<String, Long>>,
    ): List<PriceObservationEntity> = prices.map { (observedAt, periodPriceMinor) ->
        PriceObservationEntity(
            productId = productId,
            observedAt = observedAt,
            unitPriceMicros = unitPriceMicros(periodPriceMinor, 1.0),
            shelfPriceMinor = periodPriceMinor,
            packSize = 1.0,
            source = ObservationSource.BILL,
        )
    }

    private fun unitPriceMicros(shelfPriceMinor: Long, packSize: Double): Long =
        (shelfPriceMinor.toDouble() / packSize * 1_000_000.0).roundToLong()

    private companion object {
        val MONTHLY_HISTORY = listOf(
            "2025-11-01",
            "2025-12-01",
            "2026-01-01",
            "2026-02-01",
            "2026-03-01",
            "2026-04-01",
            "2026-05-01",
            "2026-06-01",
        )
        val MILK_PRICES_MINOR = listOf(150L, 152L, 154L, 157L, 160L, 162L, 165L, 168L)
        val BREAD_PRICES_MINOR = listOf(135L, 136L, 138L, 140L, 142L, 145L, 147L, 149L)
        val BROADBAND_PRICES_MINOR =
            listOf(2_800L, 2_850L, 2_900L, 2_950L, 3_000L, 3_050L, 3_100L, 3_150L)
        val ELECTRICITY_PRICES_MINOR =
            listOf(7_600L, 7_800L, 8_000L, 8_200L, 8_500L, 8_700L, 9_000L, 9_100L)
        val COUNCIL_TAX_PRICES_MINOR =
            listOf(15_000L, 15_000L, 15_000L, 15_400L, 15_400L, 15_800L, 15_800L, 16_200L)
    }
}
