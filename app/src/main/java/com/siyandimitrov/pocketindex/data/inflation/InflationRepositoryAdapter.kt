package com.siyandimitrov.pocketindex.data.inflation

import com.siyandimitrov.pocketindex.data.local.ObservationSource as StoredObservationSource
import com.siyandimitrov.pocketindex.data.local.PriceObservationDao
import com.siyandimitrov.pocketindex.data.local.UnitType as StoredUnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.domain.CategoryId
import com.siyandimitrov.pocketindex.domain.EpochDay
import com.siyandimitrov.pocketindex.domain.InflationDashboardInput
import com.siyandimitrov.pocketindex.domain.MerchantId
import com.siyandimitrov.pocketindex.domain.ObservationSource
import com.siyandimitrov.pocketindex.domain.PriceObservation
import com.siyandimitrov.pocketindex.domain.Product
import com.siyandimitrov.pocketindex.domain.ProductId
import com.siyandimitrov.pocketindex.domain.UnitPriceMicros
import com.siyandimitrov.pocketindex.domain.UnitType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Reactive boundary between Room-shaped repository rows and the pure inflation domain.
 *
 * Observation counts are used as Room's invalidation signal; each change reloads the complete
 * analytical table so confirmed receipts and recurring bill updates immediately recalculate.
 */
@Singleton
class InflationRepositoryAdapter @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val observationDao: PriceObservationDao,
) {
    fun observeDashboardInput(): Flow<InflationDashboardInput> = combine(
        catalogRepository.observeProducts(),
        catalogRepository.observeCategories(),
        catalogRepository.observeMerchants(),
        observationDao.observeAllForInflation(),
    ) { products, categories, merchants, observations ->
        val rows = SourceRows(
            products = products,
            categories = categories,
            merchantIds = merchants.mapTo(hashSetOf()) { it.id },
        )
        val domainProducts = rows.products.map { row ->
            Product(
                id = ProductId(row.product.id),
                categoryId = CategoryId(row.product.categoryId),
                unitType = row.product.unitType.toDomain(),
            )
        }
        InflationDashboardInput(
            products = domainProducts,
            observations = observations.map { analyticalRow ->
                val row = analyticalRow.observation
                PriceObservation(
                    observationId = row.id,
                    productId = ProductId(row.productId),
                    observedOn = parseIsoEpochDay(row.observedAt),
                    unitPrice = UnitPriceMicros(row.unitPriceMicros),
                    purchasedQuantityBaseUnits = purchasedQuantityBaseUnits(
                        source = row.source,
                        packSize = row.packSize,
                        receiptQuantity = analyticalRow.receiptQuantity,
                    ),
                    merchantId = row.merchantId
                        ?.takeIf(rows.merchantIds::contains)
                        ?.let(::MerchantId),
                    source = row.source.toDomain(),
                    shelfPriceMinor = row.shelfPriceMinor,
                    packSizeBaseUnits = row.packSize,
                )
            },
            productNames = rows.products.associate {
                ProductId(it.product.id) to it.product.canonicalName
            },
            categoryNames = rows.categories.associate {
                CategoryId(it.id) to it.name
            },
            categoryWeightOverrides = rows.categories.mapNotNull { category ->
                category.expenditureWeight?.let { CategoryId(category.id) to it }
            }.toMap(),
        )
    }

    private data class SourceRows(
        val products: List<com.siyandimitrov.pocketindex.data.local.ProductWithCategory>,
        val categories: List<com.siyandimitrov.pocketindex.data.local.CategoryEntity>,
        val merchantIds: Set<Long>,
    )
}

internal fun parseIsoEpochDay(value: String): EpochDay {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val parsed = runCatching { parser.parse(value) }
        .getOrNull()
        ?: throw IllegalArgumentException("Invalid ISO date: $value")
    return EpochDay(Math.floorDiv(parsed.time, 86_400_000L))
}

internal fun purchasedQuantityBaseUnits(
    source: StoredObservationSource,
    packSize: Double,
    receiptQuantity: Double?,
): Double = when (source) {
    StoredObservationSource.RECEIPT -> (receiptQuantity ?: 1.0) * packSize
    StoredObservationSource.BILL -> 1.0
    StoredObservationSource.MANUAL -> packSize
}

private fun StoredUnitType.toDomain(): UnitType = when (this) {
    StoredUnitType.MASS_G -> UnitType.MASS_G
    StoredUnitType.VOLUME_ML -> UnitType.VOLUME_ML
    StoredUnitType.COUNT -> UnitType.COUNT
    StoredUnitType.SERVICE -> UnitType.SERVICE
}

private fun StoredObservationSource.toDomain(): ObservationSource = when (this) {
    StoredObservationSource.RECEIPT -> ObservationSource.RECEIPT
    StoredObservationSource.BILL -> ObservationSource.BILL
    StoredObservationSource.MANUAL -> ObservationSource.MANUAL
}
