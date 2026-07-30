package com.siyandimitrov.pocketindex.domain

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadlineRateCalculatorTest {
    @Test
    fun `before one year rate is annualised and labelled early estimate`() {
        val result = HeadlineRateCalculator.calculate(
            listOf(
                IndexPoint(EpochDay(0), 100.0),
                IndexPoint(EpochDay(90), 102.0),
                IndexPoint(EpochDay(180), 110.0),
            ),
        )!!

        val expected = (1.1.pow(365.2425 / 180.0) - 1.0) * 100.0
        assertEquals(expected, result.percent, absoluteTolerance = 0.000001)
        assertEquals(
            HeadlineRateKind.ANNUALISED_EARLY_ESTIMATE,
            result.kind,
        )
        assertEquals(EpochDay(0), result.comparison.asOf)
    }

    @Test
    fun `after one year rate compares with latest point at or before cutoff`() {
        val result = HeadlineRateCalculator.calculate(
            listOf(
                IndexPoint(EpochDay(0), 100.0),
                IndexPoint(EpochDay(30), 105.0),
                IndexPoint(EpochDay(365), 115.0),
                IndexPoint(EpochDay(400), 120.0),
            ),
        )!!

        // Day 400's one-year cutoff is day 35, so the monthly day-30 point is used.
        assertEquals(
            (120.0 / 105.0 - 1.0) * 100.0,
            result.percent,
            absoluteTolerance = 0.000001,
        )
        assertEquals(HeadlineRateKind.YEAR_ON_YEAR, result.kind)
        assertEquals(EpochDay(30), result.comparison.asOf)
    }

    @Test
    fun `rate is absent without a non-zero time interval`() {
        assertNull(
            HeadlineRateCalculator.calculate(
                listOf(IndexPoint(EpochDay(0), 100.0)),
            ),
        )
        assertNull(
            HeadlineRateCalculator.calculate(
                listOf(
                    IndexPoint(EpochDay(0), 100.0),
                    IndexPoint(EpochDay(0), 110.0),
                ),
            ),
        )
    }
}
