package com.siyandimitrov.pocketindex.ui.basket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.ProductEntity
import com.siyandimitrov.pocketindex.data.local.ProductWithCategory
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.NewObservation
import com.siyandimitrov.pocketindex.data.repository.ObservationRepository
import com.siyandimitrov.pocketindex.data.repository.decodeAliases
import com.siyandimitrov.pocketindex.data.repository.encodeAliases
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProductStatusFilter {
    ALL,
    FRESH,
    STALE,
    FIXED_BASKET,
}

enum class ProductChartMode {
    UNIT_PRICE,
    PACK_SIZE,
}

data class BasketProductUi(
    val id: Long,
    val name: String,
    val categoryId: Long,
    val categoryName: String,
    val unitType: UnitType,
    val observationCount: Int,
    /** Times this product appeared on a confirmed receipt or bill; manual price checks excluded. */
    val purchaseCount: Int,
    val latestObservedAt: String?,
    val currentUnitPriceMicros: Double?,
    val isFresh: Boolean,
    val isFixedBasketEligible: Boolean,
)

data class MerchantPriceUi(
    val merchantName: String,
    val observedAt: String,
    val unitPriceMicros: Long,
    val shelfPriceMinor: Long,
    val packSize: Double,
)

data class ObservationUi(
    val id: Long,
    val observedAt: String,
    val merchantName: String,
    val unitPriceMicros: Long,
    val shelfPriceMinor: Long,
    val packSize: Double,
    val source: ObservationSource,
)

data class PackChangeUi(
    val observedAt: String,
    val fromSize: Double,
    val toSize: Double,
    val isShrinkflation: Boolean,
)

data class ProductDetailUi(
    val product: ProductEntity,
    val categoryName: String,
    val aliases: List<String>,
    val observations: List<ObservationUi>,
    val merchantPrices: List<MerchantPriceUi>,
    val currentMeanUnitPriceMicros: Double?,
    val packChanges: List<PackChangeUi>,
)

data class BasketUiState(
    val products: List<BasketProductUi> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val merchants: List<MerchantEntity> = emptyList(),
    val selectedProduct: ProductDetailUi? = null,
    val selectedProductId: Long? = null,
    val searchQuery: String = "",
    val categoryId: Long? = null,
    val statusFilter: ProductStatusFilter = ProductStatusFilter.ALL,
    val chartMode: ProductChartMode = ProductChartMode.UNIT_PRICE,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val filteredProducts: List<BasketProductUi>
        get() = products.filter { product ->
            val matchesQuery = searchQuery.isBlank() ||
                product.name.contains(searchQuery.trim(), ignoreCase = true) ||
                product.categoryName.contains(searchQuery.trim(), ignoreCase = true)
            val matchesCategory = categoryId == null || product.categoryId == categoryId
            val matchesStatus = when (statusFilter) {
                ProductStatusFilter.ALL -> true
                ProductStatusFilter.FRESH -> product.isFresh && product.observationCount > 0
                ProductStatusFilter.STALE -> !product.isFresh && product.observationCount > 0
                ProductStatusFilter.FIXED_BASKET -> product.isFixedBasketEligible
            }
            matchesQuery && matchesCategory && matchesStatus
        }
}

private data class BasketTransientState(
    val selectedProductId: Long? = null,
    val searchQuery: String = "",
    val categoryId: Long? = null,
    val statusFilter: ProductStatusFilter = ProductStatusFilter.ALL,
    val chartMode: ProductChartMode = ProductChartMode.UNIT_PRICE,
    val isSaving: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class BasketViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val observationRepository: ObservationRepository,
    private val requests: BasketRequests,
) : ViewModel() {
    private val transient = MutableStateFlow(BasketTransientState())

    init {
        viewModelScope.launch {
            requests.target.filterNotNull().collect { target ->
                transient.update { draft ->
                    when (target) {
                        is BasketTarget.Product ->
                            draft.copy(selectedProductId = target.productId, message = null)
                        // The whole category, not just what a leftover search or status filter
                        // would let through.
                        is BasketTarget.Category -> draft.copy(
                            selectedProductId = null,
                            categoryId = target.categoryId,
                            searchQuery = "",
                            statusFilter = ProductStatusFilter.ALL,
                            message = null,
                        )
                    }
                }
                requests.clear()
            }
        }
    }

    val uiState = combine(
        catalogRepository.observeProducts(),
        catalogRepository.observeCategories(),
        catalogRepository.observeMerchants(),
        observationRepository.observeAll(),
        transient,
    ) { products, categories, merchants, observations, draft ->
        buildBasketUiState(products, categories, merchants, observations, draft)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BasketUiState(),
    )

    fun updateSearch(query: String) = transient.update { it.copy(searchQuery = query) }

    fun selectCategory(categoryId: Long?) = transient.update { it.copy(categoryId = categoryId) }

    fun selectStatus(status: ProductStatusFilter) = transient.update { it.copy(statusFilter = status) }

    fun selectProduct(productId: Long) =
        transient.update { it.copy(selectedProductId = productId, message = null) }

    fun closeProduct() =
        transient.update { it.copy(selectedProductId = null, message = null) }

    fun selectChartMode(mode: ProductChartMode) = transient.update { it.copy(chartMode = mode) }

    fun dismissMessage() = transient.update { it.copy(message = null) }

    fun saveManualObservation(
        observedAt: String,
        priceText: String,
        packSizeText: String,
        merchantId: Long?,
    ) {
        val productId = uiState.value.selectedProductId ?: return
        val validation = validateObservationInput(observedAt, priceText, packSizeText)
        if (validation != null) {
            transient.update { it.copy(message = validation) }
            return
        }
        val shelfPriceMinor = priceText.toPriceMinor()!!
        val packSize = packSizeText.toPositiveDouble()!!
        launchSaving("Price observation added.") {
            observationRepository.addObservation(
                NewObservation(
                    productId = productId,
                    observedAt = observedAt.trim(),
                    shelfPriceMinor = shelfPriceMinor,
                    packSize = packSize,
                    merchantId = merchantId,
                ),
            )
        }
    }

    fun saveProduct(
        name: String,
        categoryId: Long?,
        unitType: UnitType,
        packSizeText: String,
        aliasesText: String,
    ) {
        val current = uiState.value.selectedProduct?.product ?: return
        val validation = validateProductInput(name, categoryId, packSizeText)
        if (validation != null) {
            transient.update { it.copy(message = validation) }
            return
        }
        launchSaving("Product updated.") {
            catalogRepository.updateProduct(
                current.copy(
                    canonicalName = name.trim(),
                    categoryId = requireNotNull(categoryId),
                    unitType = unitType,
                    packSize = packSizeText.toPositiveDouble(),
                    aliases = encodeAliases(
                        aliasesText.lines()
                            .flatMap { it.split(',') },
                    ),
                ),
            )
        }
    }

    fun mergeInto(targetProductId: Long) {
        val sourceProductId = uiState.value.selectedProductId ?: return
        launchSaving("Products merged.") {
            catalogRepository.mergeProducts(sourceProductId, targetProductId)
            transient.update { it.copy(selectedProductId = targetProductId) }
        }
    }

    private fun launchSaving(successMessage: String, operation: suspend () -> Unit) {
        transient.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess {
                    transient.update { it.copy(isSaving = false, message = successMessage) }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    transient.update {
                        it.copy(
                            isSaving = false,
                            message = error.message?.takeIf(String::isNotBlank)
                                ?: "The change could not be saved.",
                        )
                    }
                }
        }
    }
}

private fun buildBasketUiState(
    products: List<ProductWithCategory>,
    categories: List<CategoryEntity>,
    merchants: List<MerchantEntity>,
    observations: List<PriceObservationEntity>,
    draft: BasketTransientState,
): BasketUiState {
    val merchantNames = merchants.associate { it.id to it.name }
    val observationsByProduct = observations.groupBy { it.productId }
    val firstObservationDate = observations.firstOrNull()?.observedAt
    val baseWindowEnd = firstObservationDate?.plusCalendarDays(55)
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
    val productRows = products.map { relation ->
        val history = observationsByProduct[relation.product.id].orEmpty()
        val merchantCarryForward = history.latestByMerchant()
        val latestDate = merchantCarryForward.maxOfOrNull { it.observedAt }
        val baseObservations = baseWindowEnd?.let { end ->
            history.filter { it.observedAt <= end }
        }.orEmpty()
        BasketProductUi(
            id = relation.product.id,
            name = relation.product.canonicalName,
            categoryId = relation.category.id,
            categoryName = relation.category.name,
            unitType = relation.product.unitType,
            observationCount = history.size,
            purchaseCount = history.count { it.source != ObservationSource.MANUAL },
            latestObservedAt = latestDate,
            currentUnitPriceMicros = merchantCarryForward
                .map { it.unitPriceMicros.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.average(),
            isFresh = latestDate != null && (
                relation.product.unitType == UnitType.SERVICE ||
                    daysBetween(latestDate, today) <= 90
                ),
            isFixedBasketEligible = baseObservations.size >= 2 ||
                baseObservations.any { it.source == ObservationSource.BILL },
        )
    }.sortedWith(
        // Most-bought products first, so the everyday basket sits at the top of the catalogue.
        compareByDescending(BasketProductUi::purchaseCount).thenBy { it.name.lowercase() },
    )

    val selectedRelation = products.firstOrNull { it.product.id == draft.selectedProductId }
    val selectedDetail = selectedRelation?.let { relation ->
        val history = observationsByProduct[relation.product.id].orEmpty()
            .sortedWith(compareBy({ it.observedAt }, { it.id }))
        val current = history.latestByMerchant()
        ProductDetailUi(
            product = relation.product,
            categoryName = relation.category.name,
            aliases = decodeAliases(relation.product.aliases),
            observations = history.asReversed().map { observation ->
                ObservationUi(
                    id = observation.id,
                    observedAt = observation.observedAt,
                    merchantName = observation.merchantId?.let(merchantNames::get)
                        ?: "No merchant",
                    unitPriceMicros = observation.unitPriceMicros,
                    shelfPriceMinor = observation.shelfPriceMinor,
                    packSize = observation.packSize,
                    source = observation.source,
                )
            },
            merchantPrices = current
                .map { observation ->
                    MerchantPriceUi(
                        merchantName = observation.merchantId?.let(merchantNames::get)
                            ?: "No merchant",
                        observedAt = observation.observedAt,
                        unitPriceMicros = observation.unitPriceMicros,
                        shelfPriceMinor = observation.shelfPriceMinor,
                        packSize = observation.packSize,
                    )
                }
                .sortedBy { it.merchantName.lowercase() },
            currentMeanUnitPriceMicros = current
                .map { it.unitPriceMicros.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.average(),
            packChanges = history.groupBy { it.merchantId }
                .values
                .flatMap { merchantHistory ->
                    merchantHistory.zipWithNext()
                        .filter { (before, after) ->
                            !nearlyEqual(before.packSize, after.packSize)
                        }
                        .map { (before, after) ->
                            PackChangeUi(
                                observedAt = after.observedAt,
                                fromSize = before.packSize,
                                toSize = after.packSize,
                                isShrinkflation = after.packSize < before.packSize &&
                                    after.shelfPriceMinor >= before.shelfPriceMinor,
                            )
                        }
                }
                .distinct()
                .sortedBy { it.observedAt }
                .asReversed(),
        )
    }
    return BasketUiState(
        products = productRows,
        categories = categories,
        merchants = merchants,
        selectedProduct = selectedDetail,
        selectedProductId = draft.selectedProductId,
        searchQuery = draft.searchQuery,
        categoryId = draft.categoryId,
        statusFilter = draft.statusFilter,
        chartMode = draft.chartMode,
        isLoading = false,
        isSaving = draft.isSaving,
        message = draft.message,
    )
}

private fun List<PriceObservationEntity>.latestByMerchant(): List<PriceObservationEntity> =
    groupBy { it.merchantId }
        .values
        .map { merchantHistory ->
            merchantHistory.maxWith(compareBy({ it.observedAt }, { it.id }))
        }

private fun String.plusCalendarDays(days: Int): String? {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.UK).apply { isLenient = false }
    val parsed = runCatching { formatter.parse(this) }.getOrNull() ?: return null
    return Calendar.getInstance().run {
        time = parsed
        add(Calendar.DAY_OF_YEAR, days)
        formatter.format(time)
    }
}

private fun daysBetween(start: String, end: String): Long {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.UK).apply { isLenient = false }
    val startDate = runCatching { formatter.parse(start) }.getOrNull() ?: return Long.MAX_VALUE
    val endDate = runCatching { formatter.parse(end) }.getOrNull() ?: return Long.MAX_VALUE
    return ((endDate.time - startDate.time) / 86_400_000L).coerceAtLeast(0)
}

internal fun validateObservationInput(
    observedAt: String,
    priceText: String,
    packSizeText: String,
): String? = when {
    !observedAt.isIsoDate() -> "Enter a valid date in YYYY-MM-DD format."
    priceText.toPriceMinor() == null -> "Enter a positive price with no more than two decimals."
    packSizeText.toPositiveDouble() == null -> "Enter a pack size greater than zero."
    else -> null
}

internal fun validateProductInput(
    name: String,
    categoryId: Long?,
    packSizeText: String,
): String? = when {
    name.trim().isEmpty() -> "Product name is required."
    categoryId == null -> "Choose a category."
    packSizeText.isNotBlank() && packSizeText.toPositiveDouble() == null ->
        "Typical pack size must be greater than zero."
    else -> null
}

private fun String.isIsoDate(): Boolean {
    if (!matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return false
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.UK).apply { isLenient = false }
    return runCatching { formatter.parse(this) }.getOrNull() != null
}

private fun String.toPositiveDouble(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }

private fun String.toPriceMinor(): Long? {
    val normalised = trim().removePrefix("£").trim()
    return runCatching {
        normalised.toBigDecimal()
            .also { require(it.signum() > 0 && it.scale() <= 2) }
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()
}

private fun nearlyEqual(left: Double, right: Double): Boolean =
    kotlin.math.abs(left - right) < 0.000_001
