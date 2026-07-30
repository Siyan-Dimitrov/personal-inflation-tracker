package com.siyandimitrov.pocketindex.domain

import kotlin.math.abs

data class CarriedForwardPrice(
    val meanUnitPriceMicros: Double,
    val lastObservedOn: EpochDay,
    val merchantUnitPricesMicros: Map<MerchantId?, Long>,
)

/**
 * Resolves the latest price at or before a date independently for each merchant, then averages the
 * merchant prices. Observations after the requested date never influence the result.
 */
class PriceHistory(observations: List<PriceObservation>) {
    private val observations = observations
        .sortedWith(
            compareBy<PriceObservation> { it.observedOn.value }
                .thenBy { it.observationId },
        )
        .toList()

    init {
        require(observations.isNotEmpty()) { "A price history cannot be empty." }
        require(observations.map { it.productId }.distinct().size == 1) {
            "A price history must contain observations for one product."
        }
    }

    fun priceAt(asOf: EpochDay): CarriedForwardPrice? {
        val eligible = observations.filter { it.observedOn <= asOf }
        if (eligible.isEmpty()) return null

        val latestByMerchant = eligible
            .groupBy { it.merchantId }
            .mapValues { (_, merchantObservations) ->
                merchantObservations.maxWith(
                    compareBy<PriceObservation> { it.observedOn.value }
                        .thenBy { it.observationId },
                )
            }
        return CarriedForwardPrice(
            meanUnitPriceMicros = latestByMerchant.values
                .map { it.unitPrice.value.toDouble() }
                .average(),
            lastObservedOn = latestByMerchant.values.maxOf { it.observedOn },
            merchantUnitPricesMicros = latestByMerchant
                .mapValues { (_, observation) -> observation.unitPrice.value }
                .toMap(),
        )
    }
}

data class BasketProduct(
    val product: Product,
    val baseQuantity: Double,
    val baseUnitPriceMicros: Double,
    val baseExpenditureMicros: Double,
)

data class ProductIndexResult(
    val productId: ProductId,
    val baseQuantity: Double,
    val baseUnitPriceMicros: Double,
    val currentUnitPriceMicros: Double,
    val index: Double,
    val lastObservedOn: EpochDay,
    val isStale: Boolean,
)

data class CategoryIndexResult(
    val categoryId: CategoryId,
    val weight: Double,
    val index: Double,
    val freshCoveragePercent: Double,
    val products: List<ProductIndexResult>,
)

data class IndexSnapshot(
    val asOf: EpochDay,
    val headlineIndex: Double,
    val freshCoveragePercent: Double,
    val categories: List<CategoryIndexResult>,
) {
    val products: List<ProductIndexResult>
        get() = categories.flatMap { it.products }
}

/**
 * A fixed Laspeyres basket. Products, quantities, base prices, and category weights cannot change
 * after construction; only carried-forward prices vary between snapshots.
 */
class FixedBasket internal constructor(
    val baseWindow: BaseWindow,
    basketProducts: List<BasketProduct>,
    histories: Map<ProductId, PriceHistory>,
    categoryWeights: Map<CategoryId, Double>,
    private val staleAfterDays: Long,
) {
    val products: List<BasketProduct> = basketProducts.toList()
    val productIds: Set<ProductId> = products.mapTo(linkedSetOf()) { it.product.id }
    val categoryWeights: Map<CategoryId, Double> = categoryWeights.toMap()
    private val histories: Map<ProductId, PriceHistory> = histories.toMap()

    init {
        require(products.isNotEmpty()) { "A fixed basket must contain at least one product." }
        require(productIds.size == products.size) { "Basket products must be unique." }
        require(categoryWeights.values.all { it.isFinite() && it >= 0.0 }) {
            "Category weights must be finite and non-negative."
        }
        require(abs(categoryWeights.values.sum() - 1.0) < WEIGHT_TOLERANCE) {
            "Category weights must sum to one."
        }
    }

    fun snapshot(asOf: EpochDay): IndexSnapshot {
        require(asOf >= baseWindow.endInclusive) {
            "A fixed-basket index cannot be evaluated before the base window ends."
        }

        val resultsByCategory = products
            .groupBy { it.product.categoryId }
            .mapValues { (_, categoryProducts) ->
                categoryProducts.map { basketProduct ->
                    val current = checkNotNull(histories.getValue(basketProduct.product.id).priceAt(asOf)) {
                        "A base product must have a carried-forward price."
                    }
                    val ageDays = asOf.daysSince(current.lastObservedOn)
                    val stale = basketProduct.product.isGroceryType && ageDays > staleAfterDays
                    ProductIndexResult(
                        productId = basketProduct.product.id,
                        baseQuantity = basketProduct.baseQuantity,
                        baseUnitPriceMicros = basketProduct.baseUnitPriceMicros,
                        currentUnitPriceMicros = current.meanUnitPriceMicros,
                        index = current.meanUnitPriceMicros /
                            basketProduct.baseUnitPriceMicros * INDEX_BASE,
                        lastObservedOn = current.lastObservedOn,
                        isStale = stale,
                    )
                }
            }

        val categories = resultsByCategory
            .map { (categoryId, productResults) ->
                val categoryBasketProducts = products.filter {
                    it.product.categoryId == categoryId
                }
                val baseExpenditure = categoryBasketProducts.sumOf {
                    it.baseExpenditureMicros
                }
                val currentExpenditure = categoryBasketProducts.sumOf { basketProduct ->
                    val result = productResults.first {
                        it.productId == basketProduct.product.id
                    }
                    basketProduct.baseQuantity * result.currentUnitPriceMicros
                }
                val freshBaseExpenditure = categoryBasketProducts.sumOf { basketProduct ->
                    val result = productResults.first {
                        it.productId == basketProduct.product.id
                    }
                    if (result.isStale) 0.0 else basketProduct.baseExpenditureMicros
                }
                CategoryIndexResult(
                    categoryId = categoryId,
                    weight = categoryWeights.getValue(categoryId),
                    index = currentExpenditure / baseExpenditure * INDEX_BASE,
                    freshCoveragePercent = freshBaseExpenditure /
                        baseExpenditure * PERCENT,
                    products = productResults.sortedBy { it.productId.value },
                )
            }
            .sortedBy { it.categoryId.value }

        return IndexSnapshot(
            asOf = asOf,
            headlineIndex = categories.sumOf { it.weight * it.index },
            freshCoveragePercent = categories.sumOf {
                it.weight * it.freshCoveragePercent
            },
            categories = categories,
        )
    }

    fun series(asOfDates: List<EpochDay>): List<IndexSnapshot> =
        asOfDates.map(::snapshot)

    private companion object {
        const val INDEX_BASE = 100.0
        const val PERCENT = 100.0
        const val WEIGHT_TOLERANCE = 1e-9
    }
}

/**
 * Builds the first-eight-week fixed basket and calculates its category Laspeyres indices.
 */
class PersonalInflationCalculator(
    private val configuration: IndexConfiguration = IndexConfiguration(),
) {
    fun buildFixedBasket(
        products: Collection<Product>,
        observations: Collection<PriceObservation>,
        baseWindow: BaseWindow? = null,
        categoryWeightOverrides: Map<CategoryId, Double> = emptyMap(),
    ): FixedBasket {
        require(products.isNotEmpty()) { "Products cannot be empty." }
        require(observations.isNotEmpty()) { "Observations cannot be empty." }

        val productsById = products.associateBy { it.id }
        require(productsById.size == products.size) { "Product ids must be unique." }
        require(observations.all { it.productId in productsById }) {
            "Every observation must refer to a supplied product."
        }

        val effectiveBaseWindow = baseWindow ?: BaseWindow.startingOn(
            firstDay = observations.minOf { it.observedOn },
            durationDays = configuration.baseWindowDays,
        )
        val observationsByProduct = observations
            .groupBy { it.productId }
            .mapValues { (_, values) -> values.toList() }

        val basketProducts = productsById.values
            .sortedBy { it.id.value }
            .mapNotNull { product ->
                val productObservations = observationsByProduct[product.id].orEmpty()
                val baseObservations = productObservations.filter {
                    it.observedOn in effectiveBaseWindow
                }
                val eligible = baseObservations.size >= MINIMUM_BASE_OBSERVATIONS ||
                    baseObservations.any { it.source == ObservationSource.BILL }
                if (!eligible) return@mapNotNull null

                val baseQuantity = baseObservations.sumOf {
                    it.purchasedQuantityBaseUnits
                }
                val basePrice = PriceHistory(productObservations)
                    .priceAt(effectiveBaseWindow.endInclusive)
                    ?.meanUnitPriceMicros
                    ?: return@mapNotNull null
                require(basePrice > 0.0) {
                    "Base unit price must be positive for product ${product.id.value}."
                }
                BasketProduct(
                    product = product,
                    baseQuantity = baseQuantity,
                    baseUnitPriceMicros = basePrice,
                    baseExpenditureMicros = baseQuantity * basePrice,
                )
            }

        require(basketProducts.isNotEmpty()) {
            "No product met the fixed-basket eligibility rules."
        }

        val histories = basketProducts.associate { basketProduct ->
            basketProduct.product.id to PriceHistory(
                observationsByProduct.getValue(basketProduct.product.id),
            )
        }
        val baseExpenditureByCategory = basketProducts
            .groupBy { it.product.categoryId }
            .mapValues { (_, categoryProducts) ->
                categoryProducts.sumOf { it.baseExpenditureMicros }
            }
        val weights = resolveCategoryWeights(
            baseExpenditureByCategory = baseExpenditureByCategory,
            overrides = categoryWeightOverrides,
        )

        return FixedBasket(
            baseWindow = effectiveBaseWindow,
            basketProducts = basketProducts,
            histories = histories,
            categoryWeights = weights,
            staleAfterDays = configuration.staleAfterDays,
        )
    }

    private fun resolveCategoryWeights(
        baseExpenditureByCategory: Map<CategoryId, Double>,
        overrides: Map<CategoryId, Double>,
    ): Map<CategoryId, Double> {
        require(overrides.keys.all { it in baseExpenditureByCategory }) {
            "A category weight was supplied for a category outside the basket."
        }
        require(overrides.values.all { it.isFinite() && it in 0.0..1.0 }) {
            "Category weight overrides must be finite fractions from zero to one."
        }

        if (overrides.isEmpty()) {
            val totalExpenditure = baseExpenditureByCategory.values.sum()
            return baseExpenditureByCategory.mapValues { (_, expenditure) ->
                expenditure / totalExpenditure
            }
        }

        val overriddenTotal = overrides.values.sum()
        require(overriddenTotal <= 1.0 + WEIGHT_TOLERANCE) {
            "Category weight overrides cannot sum to more than one."
        }
        val automaticCategories = baseExpenditureByCategory.keys - overrides.keys
        if (automaticCategories.isEmpty()) {
            require(abs(overriddenTotal - 1.0) < WEIGHT_TOLERANCE) {
                "A complete set of category overrides must sum to one."
            }
            return overrides.toMap()
        }

        val remainingWeight = (1.0 - overriddenTotal).coerceAtLeast(0.0)
        val remainingExpenditure = automaticCategories.sumOf {
            baseExpenditureByCategory.getValue(it)
        }
        return buildMap {
            putAll(overrides)
            automaticCategories.forEach { categoryId ->
                put(
                    categoryId,
                    remainingWeight *
                        baseExpenditureByCategory.getValue(categoryId) /
                        remainingExpenditure,
                )
            }
        }
    }

    private companion object {
        const val MINIMUM_BASE_OBSERVATIONS = 2
        const val WEIGHT_TOLERANCE = 1e-9
    }
}
