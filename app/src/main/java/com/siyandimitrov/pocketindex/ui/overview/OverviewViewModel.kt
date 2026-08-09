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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

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
    ) : OverviewUiState

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
) : ViewModel() {
    val uiState = combine(
        adapter.observeDashboardInput(),
        preferences.baseWindowDays,
        preferences.chartRangeMonths,
    ) { input, baseWindowDays, chartRangeMonths ->
        Triple(input, baseWindowDays, chartRangeMonths)
    }.mapLatest { (input, baseWindowDays, chartRangeMonths) ->
        calculateOverviewState(
            input = input,
            asOf = EpochDay(Math.floorDiv(Date().time, MILLIS_PER_DAY)),
            configuration = IndexConfiguration(baseWindowDays = baseWindowDays),
            chartRange = InflationChartRange.fromPreference(chartRangeMonths),
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

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

internal fun calculateOverviewState(
    input: InflationDashboardInput,
    asOf: EpochDay,
    configuration: IndexConfiguration,
    chartRange: InflationChartRange = InflationChartRange.SIX_MONTHS,
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
                    OverviewUiState.Ready(calculation, chartRange)
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
