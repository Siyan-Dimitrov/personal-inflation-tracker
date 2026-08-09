package com.siyandimitrov.pocketindex.ui.overview

import com.siyandimitrov.pocketindex.domain.EpochDay
import com.siyandimitrov.pocketindex.domain.IndexPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverviewChartRangeTest {
    private val series = (0L..14L).map { month ->
        IndexPoint(asOf = EpochDay(month), index = 100.0 + month)
    }

    @Test
    fun `range keeps a comparison endpoint and the requested number of months`() {
        assertEquals(2, visibleSeriesForRange(series, InflationChartRange.ONE_MONTH).size)
        assertEquals(7, visibleSeriesForRange(series, InflationChartRange.SIX_MONTHS).size)
        assertEquals(13, visibleSeriesForRange(series, InflationChartRange.ONE_YEAR).size)
        assertEquals(series, visibleSeriesForRange(series, InflationChartRange.FIVE_YEARS))
        assertEquals(series, visibleSeriesForRange(series, InflationChartRange.ALL))
    }

    @Test
    fun `a fixed range only earns a score once the index spans it`() {
        val twoMonths = series.take(2)
        assertEquals(
            rangeChangePercent(twoMonths),
            displayedRangePercent(twoMonths, InflationChartRange.ONE_MONTH),
        )
        assertNull(displayedRangePercent(twoMonths, InflationChartRange.SIX_MONTHS))
        assertNull(displayedRangePercent(twoMonths, InflationChartRange.ONE_YEAR))
        assertNull(displayedRangePercent(twoMonths, InflationChartRange.FIVE_YEARS))
        // The all-history view shows the plain change across the series, never annualised.
        assertEquals(
            rangeChangePercent(twoMonths),
            displayedRangePercent(twoMonths, InflationChartRange.ALL),
        )
        assertNull(displayedRangePercent(series.take(1), InflationChartRange.ALL))
        // Fifteen points span more than a year, so the one-year change is real.
        assertEquals(
            rangeChangePercent(visibleSeriesForRange(series, InflationChartRange.ONE_YEAR)),
            displayedRangePercent(
                visibleSeriesForRange(series, InflationChartRange.ONE_YEAR),
                InflationChartRange.ONE_YEAR,
            ),
        )
    }

    @Test
    fun `range change compares first and last visible index points`() {
        assertEquals(
            10.0,
            rangeChangePercent(
                listOf(
                    IndexPoint(EpochDay(1), 100.0),
                    IndexPoint(EpochDay(2), 110.0),
                ),
            )!!,
            1e-8,
        )
        assertNull(rangeChangePercent(series.take(1)))
    }

    @Test
    fun `unknown persisted range falls back to six months`() {
        assertEquals(InflationChartRange.SIX_MONTHS, InflationChartRange.fromPreference(99))
    }
}
