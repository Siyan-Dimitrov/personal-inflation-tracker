package com.siyandimitrov.pocketindex.domain

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.math.abs

data class InflationDashboardInput(
    val products: List<Product>,
    val observations: List<PriceObservation>,
    val productNames: Map<ProductId, String>,
    val categoryNames: Map<CategoryId, String>,
    val categoryWeightOverrides: Map<CategoryId, Double> = emptyMap(),
)

sealed interface InflationDashboardCalculation {
    data class BuildingBaseBasket(
        val firstObservationOn: EpochDay?,
        val baseWindowEndsOn: EpochDay?,
        val productCount: Int,
        val observationCount: Int,
        val eligibleProductCount: Int,
    ) : InflationDashboardCalculation

    data class Ready(
        val baseWindow: BaseWindow,
        val headlineRate: HeadlineRate?,
        val currentFixedIndex: Double,
        val fixedSeries: List<IndexPoint>,
        val chainedSeries: List<IndexPoint>?,
        val fixedToChainedGapPercent: Double?,
        val freshCoveragePercent: Double,
        val staleProducts: List<StaleProduct>,
        val categories: List<CategoryContribution>,
        val productContributions: List<ProductContribution>,
    ) : InflationDashboardCalculation
}

data class CategoryContribution(
    val categoryId: CategoryId,
    val name: String,
    val weight: Double,
    val currentIndex: Double,
    val changePercent: Double,
    val contributionPercentagePoints: Double,
)

data class ProductContribution(
    val productId: ProductId,
    val name: String,
    val categoryName: String,
    val changePercent: Double,
    val contributionPercentagePoints: Double,
)

data class StaleProduct(
    val productId: ProductId,
    val name: String,
    val categoryName: String,
    val lastObservedOn: EpochDay,
    val ageDays: Long,
)

/**
 * Builds the presentation-ready index result while leaving persisted names and settings traceable
 * to their domain ids. All monetary and index arithmetic remains delegated to the core engine.
 */
object InflationDashboardCalculator {
    private const val CHAIN_INTERVAL_DAYS = 365L
    private const val MILLIS_PER_DAY = 86_400_000L

    fun calculate(
        input: InflationDashboardInput,
        asOf: EpochDay,
        configuration: IndexConfiguration = IndexConfiguration(),
    ): InflationDashboardCalculation {
        if (input.products.isEmpty() || input.observations.isEmpty()) {
            return InflationDashboardCalculation.BuildingBaseBasket(
                firstObservationOn = input.observations.minOfOrNull { it.observedOn },
                baseWindowEndsOn = null,
                productCount = input.products.size,
                observationCount = input.observations.size,
                eligibleProductCount = 0,
            )
        }

        val firstObservation = input.observations.minOf { it.observedOn }
        val baseWindow = BaseWindow.startingOn(firstObservation, configuration.baseWindowDays)
        val eligibleProducts = eligibleProductIds(input, baseWindow)
        if (asOf < baseWindow.endInclusive || eligibleProducts.isEmpty()) {
            return InflationDashboardCalculation.BuildingBaseBasket(
                firstObservationOn = firstObservation,
                baseWindowEndsOn = baseWindow.endInclusive,
                productCount = input.products.size,
                observationCount = input.observations.size,
                eligibleProductCount = eligibleProducts.size,
            )
        }

        val fixedBasket = PersonalInflationCalculator(configuration).buildFixedBasket(
            products = input.products,
            observations = input.observations,
            baseWindow = baseWindow,
            categoryWeightOverrides = overridesForEligibleBasket(
                input = input,
                eligibleProducts = eligibleProducts,
            ),
        )
        val fixedDates = monthlyDates(baseWindow.endInclusive, asOf)
        val snapshots = fixedBasket.series(fixedDates)
        val fixedSeries = snapshots.map { IndexPoint(it.asOf, it.headlineIndex) }
        val headlineRate = HeadlineRateCalculator.calculate(fixedSeries)
        val current = snapshots.last()
        val comparison = headlineRate
            ?.comparison
            ?.asOf
            ?.let(fixedBasket::snapshot)
            ?: snapshots.first()
        val displayedRate = headlineRate?.percent
        val rawRate = current.headlineIndex / comparison.headlineIndex * 100.0 - 100.0
        val contributionScale = if (
            displayedRate != null &&
            abs(rawRate) > 1e-12
        ) {
            displayedRate / rawRate
        } else {
            1.0
        }

        val categories = current.categories.map { currentCategory ->
            val comparisonCategory = comparison.categories.first {
                it.categoryId == currentCategory.categoryId
            }
            val indexPointContribution =
                currentCategory.weight * (currentCategory.index - comparisonCategory.index)
            CategoryContribution(
                categoryId = currentCategory.categoryId,
                name = input.categoryNames[currentCategory.categoryId]
                    ?: "Category ${currentCategory.categoryId.value}",
                weight = currentCategory.weight,
                currentIndex = currentCategory.index,
                changePercent = currentCategory.index / comparisonCategory.index * 100.0 - 100.0,
                contributionPercentagePoints = indexPointContribution /
                    comparison.headlineIndex * 100.0 * contributionScale,
            )
        }.sortedByDescending { abs(it.contributionPercentagePoints) }

        val productContributions = current.categories.flatMap { currentCategory ->
            val comparisonCategory = comparison.categories.first {
                it.categoryId == currentCategory.categoryId
            }
            val baseExpenditure = currentCategory.products.sumOf {
                it.baseQuantity * it.baseUnitPriceMicros
            }
            currentCategory.products.map { currentProduct ->
                val comparisonProduct = comparisonCategory.products.first {
                    it.productId == currentProduct.productId
                }
                val indexPointContribution = currentCategory.weight *
                    currentProduct.baseQuantity *
                    (currentProduct.currentUnitPriceMicros -
                        comparisonProduct.currentUnitPriceMicros) /
                    baseExpenditure * 100.0
                ProductContribution(
                    productId = currentProduct.productId,
                    name = input.productNames[currentProduct.productId]
                        ?: "Product ${currentProduct.productId.value}",
                    categoryName = input.categoryNames[currentCategory.categoryId]
                        ?: "Category ${currentCategory.categoryId.value}",
                    changePercent = currentProduct.currentUnitPriceMicros /
                        comparisonProduct.currentUnitPriceMicros * 100.0 - 100.0,
                    contributionPercentagePoints = indexPointContribution /
                        comparison.headlineIndex * 100.0 * contributionScale,
                )
            }
        }.filter { abs(it.contributionPercentagePoints) > 1e-9 }
            .sortedByDescending { abs(it.contributionPercentagePoints) }

        val staleProducts = current.categories.flatMap { category ->
            category.products.filter { it.isStale }.map { product ->
                StaleProduct(
                    productId = product.productId,
                    name = input.productNames[product.productId]
                        ?: "Product ${product.productId.value}",
                    categoryName = input.categoryNames[category.categoryId]
                        ?: "Category ${category.categoryId.value}",
                    lastObservedOn = product.lastObservedOn,
                    ageDays = asOf.daysSince(product.lastObservedOn),
                )
            }
        }.sortedByDescending { it.ageDays }

        val chainedSeries = chainedSeriesOrNull(
            input = input,
            configuration = configuration,
            initialBasket = fixedBasket,
            fixedDates = fixedDates,
            asOf = asOf,
        )
        val chainedCurrent = chainedSeries?.lastOrNull()?.index
        return InflationDashboardCalculation.Ready(
            baseWindow = baseWindow,
            headlineRate = headlineRate,
            currentFixedIndex = current.headlineIndex,
            fixedSeries = fixedSeries,
            chainedSeries = chainedSeries,
            fixedToChainedGapPercent = chainedCurrent?.let {
                it / current.headlineIndex * 100.0 - 100.0
            },
            freshCoveragePercent = current.freshCoveragePercent,
            staleProducts = staleProducts,
            categories = categories,
            productContributions = productContributions,
        )
    }

    private fun chainedSeriesOrNull(
        input: InflationDashboardInput,
        configuration: IndexConfiguration,
        initialBasket: FixedBasket,
        fixedDates: List<EpochDay>,
        asOf: EpochDay,
    ): List<IndexPoint>? {
        val firstChainDay = initialBasket.baseWindow.endInclusive
        if (asOf.daysSince(firstChainDay) < CHAIN_INTERVAL_DAYS) return null

        val basketsByStart = linkedMapOf(firstChainDay to initialBasket)
        var rebaseDay = firstChainDay.plusDays(CHAIN_INTERVAL_DAYS)
        while (rebaseDay <= asOf) {
            val window = BaseWindow(
                startInclusive = rebaseDay.plusDays(-(configuration.baseWindowDays - 1)),
                endInclusive = rebaseDay,
            )
            val eligibleProducts = eligibleProductIds(input, window)
            if (eligibleProducts.isEmpty()) return null
            val basket = runCatching {
                PersonalInflationCalculator(configuration).buildFixedBasket(
                    products = input.products,
                    observations = input.observations,
                    baseWindow = window,
                    categoryWeightOverrides = overridesForEligibleBasket(
                        input = input,
                        eligibleProducts = eligibleProducts,
                    ),
                )
            }.getOrNull() ?: return null
            basketsByStart[rebaseDay] = basket
            rebaseDay = rebaseDay.plusDays(CHAIN_INTERVAL_DAYS)
        }

        val chainDates = (fixedDates + basketsByStart.keys)
            .distinct()
            .sortedBy { it.value }
        val links = chainDates.zipWithNext { from, to ->
            val basket = basketsByStart.entries.last { it.key <= from }.value
            BasketLink(basket, from, to)
        }
        if (links.isEmpty()) return null
        return ChainedIndexCalculator.calculate(links).map {
            IndexPoint(it.asOf, it.index)
        }
    }

    private fun eligibleProductIds(
        input: InflationDashboardInput,
        window: BaseWindow,
    ): Set<ProductId> = input.observations
        .filter { it.observedOn in window }
        .groupBy { it.productId }
        .filterValues { observations ->
            observations.size >= 2 ||
                observations.any { it.source == ObservationSource.BILL }
        }
        .keys

    private fun overridesForEligibleBasket(
        input: InflationDashboardInput,
        eligibleProducts: Set<ProductId>,
    ): Map<CategoryId, Double> {
        val eligibleCategories = input.products
            .filter { it.id in eligibleProducts }
            .mapTo(hashSetOf()) { it.categoryId }
        val applicable = input.categoryWeightOverrides.filterKeys(eligibleCategories::contains)
        if (applicable.keys != eligibleCategories) return applicable

        // A persisted category can legitimately have no eligible basket product. When every
        // category that remains has an override, rescale those available shares to the required
        // headline total instead of making an otherwise valid basket uncalculable.
        val total = applicable.values.sum()
        return when {
            total <= 0.0 -> emptyMap()
            abs(total - 1.0) > 1e-9 ->
                applicable.mapValues { (_, weight) -> weight / total }
            else -> applicable
        }
    }

    private fun monthlyDates(start: EpochDay, end: EpochDay): List<EpochDay> {
        require(end >= start)
        if (end == start) return listOf(start)
        val dates = mutableListOf(start)
        // The base point already stands for its own calendar month, so the monthly points
        // resume from the end of the following month. Taking the base month's end as well
        // would put two points, and two identical month labels, inside one month.
        val cursor = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = Math.multiplyExact(start.value, MILLIS_PER_DAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        while (cursor.timeInMillis / MILLIS_PER_DAY < end.value) {
            dates += EpochDay(cursor.timeInMillis / MILLIS_PER_DAY)
            cursor.add(Calendar.MONTH, 1)
            cursor.set(Calendar.DAY_OF_MONTH, cursor.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        if (dates.last() != end) dates += end
        return dates.distinct().sortedBy { it.value }
    }
}
