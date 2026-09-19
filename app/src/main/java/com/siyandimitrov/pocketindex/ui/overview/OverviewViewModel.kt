package com.siyandimitrov.pocketindex.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.inflation.InflationRepositoryAdapter
import com.siyandimitrov.pocketindex.data.preferences.InflationPreferences
import com.siyandimitrov.pocketindex.domain.EpochDay
import com.siyandimitrov.pocketindex.domain.IndexConfiguration
import com.siyandimitrov.pocketindex.domain.IndexPoint
import com.siyandimitrov.pocketindex.domain.InflationDashboardCalculation
import com.siyandimitrov.pocketindex.domain.InflationDashboardCalculator
import com.siyandimitrov.pocketindex.domain.InflationDashboardInput
import com.siyandimitrov.pocketindex.domain.MerchantId
import com.siyandimitrov.pocketindex.domain.MerchantIndex
import com.siyandimitrov.pocketindex.ui.basket.BasketRequests
import com.siyandimitrov.pocketindex.ui.basket.BasketTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface OverviewUiState {
    data object Loading : OverviewUiState

    data class Empty(
        val title: String,
        val explanation: String,
    ) : OverviewUiState

    data class BuildingBaseBasket(
        val observationCount: Int,
        val eligibleProductCount: Int,
        val daysRemaining: Long,
    ) : OverviewUiState

    data class Ready(
        val dashboard: InflationDashboardCalculation.Ready,
        val chartRange: InflationChartRange,
        /** The shop whose index the chart shows; null shows the headline basket. */
        val selectedMerchantId: MerchantId? = null,
    ) : OverviewUiState {
        val selectedMerchant: MerchantIndex?
            get() = selectedMerchantId?.let { id -> dashboard.merchants.firstOrNull { it.merchantId == id } }
    }

    data class Error(
        val explanation: String,
    ) : OverviewUiState
}

enum class InflationChartRange(
    val months: Int?,
    val shortLabel: String,
    val periodLabel: String,
) {
    ONE_MONTH(1, "1M", "1 month"),
    SIX_MONTHS(6, "6M", "6 months"),
    ONE_YEAR(12, "1Y", "1 year"),
    FIVE_YEARS(60, "5Y", "5 years"),
    ALL(null, "Max", "All history"),
    ;

    val preferenceValue: Int
        get() = months ?: 0

    companion object {
        fun fromPreference(value: Int): InflationChartRange =
            entries.firstOrNull { it.preferenceValue == value } ?: SIX_MONTHS
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    adapter: InflationRepositoryAdapter,
    private val preferences: InflationPreferences,
    private val basketRequests: BasketRequests,
) : ViewModel() {
    // Session-only: the shop filter is a way of looking at the chart, not a setting.
    private val selectedMerchantId = MutableStateFlow<MerchantId?>(null)

    val includeBills = preferences.includeBills.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = true,
    )

    val uiState = combine(
        adapter.observeDashboardInput(),
        preferences.baseWindowDays,
        preferences.chartRangeMonths,
        preferences.includeBills,
        selectedMerchantId,
    ) { input, baseWindowDays, chartRangeMonths, includeBills, merchantId ->
        OverviewInputs(input, baseWindowDays, chartRangeMonths, includeBills, merchantId)
    }.mapLatest { inputs ->
        calculateOverviewState(
            input = if (inputs.includeBills) inputs.input else inputs.input.withoutBills(),
            asOf = EpochDay(Math.floorDiv(Date().time, MILLIS_PER_DAY)),
            configuration = IndexConfiguration(baseWindowDays = inputs.baseWindowDays),
            chartRange = InflationChartRange.fromPreference(inputs.chartRangeMonths),
            selectedMerchantId = inputs.selectedMerchantId,
        )
    }.catch { throwable ->
        emit(
            OverviewUiState.Error(
                explanation = throwable.message
                    ?: "The saved observations could not be calculated.",
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        // The cached dashboard is dropped once nothing observes it, so a reset performed on
        // another tab cannot flash the old headline and chart when this screen resumes.
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 5_000,
            replayExpirationMillis = 0,
        ),
        initialValue = OverviewUiState.Loading,
    )

    fun selectChartRange(range: InflationChartRange) {
        preferences.setChartRangeMonths(range.preferenceValue)
    }

    fun setIncludeBills(include: Boolean) {
        preferences.setIncludeBills(include)
    }

    /** Parks the request for the basket tab; the caller then navigates there. */
    fun openInBasket(target: BasketTarget) {
        basketRequests.open(target)
    }

    /** Tapping the selected shop again returns the chart to the headline basket. */
    fun toggleMerchant(merchantId: MerchantId) {
        selectedMerchantId.update { current -> if (current == merchantId) null else merchantId }
    }

    private data class OverviewInputs(
        val input: InflationDashboardInput,
        val baseWindowDays: Long,
        val chartRangeMonths: Int,
        val includeBills: Boolean,
        val selectedMerchantId: MerchantId?,
    )

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

internal fun calculateOverviewState(
    input: InflationDashboardInput,
    asOf: EpochDay,
    configuration: IndexConfiguration,
    chartRange: InflationChartRange = InflationChartRange.SIX_MONTHS,
    selectedMerchantId: MerchantId? = null,
): OverviewUiState {
    if (input.products.isEmpty()) {
        return OverviewUiState.Empty(
            title = "Start your personal index",
            explanation = "Scan a receipt or add a recurring bill to create your first product.",
        )
    }
    if (input.observations.isEmpty()) {
        return OverviewUiState.Empty(
            title = "No confirmed prices yet",
            explanation = "Confirm a receipt or add a price observation to begin the base basket.",
        )
    }
    return runCatching {
        InflationDashboardCalculator.calculate(input, asOf, configuration)
    }.fold(
        onSuccess = { calculation ->
            when (calculation) {
                is InflationDashboardCalculation.BuildingBaseBasket -> {
                    OverviewUiState.BuildingBaseBasket(
                        observationCount = calculation.observationCount,
                        eligibleProductCount = calculation.eligibleProductCount,
                        daysRemaining = calculation.baseWindowEndsOn
                            ?.daysSince(asOf)
                            ?.coerceAtLeast(0)
                            ?: 0,
                    )
                }

                is InflationDashboardCalculation.Ready -> {
                    OverviewUiState.Ready(
                        dashboard = calculation,
                        chartRange = chartRange,
                        // A shop that lost its rate (say, after a receipt was deleted) must not
                        // leave the chart pointing at a series that no longer exists.
                        selectedMerchantId = selectedMerchantId?.takeIf { id ->
                            calculation.merchants.any { it.merchantId == id && it.series != null }
                        },
                    )
                }
            }
        },
        onFailure = { throwable ->
            OverviewUiState.Error(
                explanation = throwable.message
                    ?: "The saved observations could not be calculated.",
            )
        },
    )
}

/**
 * Drops recurring bills (service products and their observations) so the index reflects shop
 * prices only. A fixed-price contract whose bill changes reflects usage, not inflation.
 */
internal fun InflationDashboardInput.withoutBills(): InflationDashboardInput {
    val groceryProducts = products.filter { it.isGroceryType }
    val groceryIds = groceryProducts.mapTo(hashSetOf()) { it.id }
    return copy(
        products = groceryProducts,
        observations = observations.filter { it.productId in groceryIds },
    )
}

internal fun visibleSeriesForRange(
    series: List<IndexPoint>,
    range: InflationChartRange,
): List<IndexPoint> = range.months
    ?.let { months -> series.takeLast(months + 1) }
    ?: series

internal fun rangeChangePercent(series: List<IndexPoint>): Double? {
    if (series.size < 2) return null
    val first = series.first().index
    if (first == 0.0) return null
    return series.last().index / first * 100.0 - 100.0
}

/**
 * The score shown for a chart range: the plain change across the visible series, never
 * annualised — projections are subtitle material. A fixed range only earns a score once the
 * index actually spans it; two monthly points must not present themselves as a six-month change.
 */
internal fun displayedRangePercent(
    visibleSeries: List<IndexPoint>,
    range: InflationChartRange,
): Double? {
    val months = range.months
    if (months != null && visibleSeries.size - 1 < months) return null
    return rangeChangePercent(visibleSeries)
}
