package com.siyandimitrov.pocketindex.data.repository

import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import kotlin.test.Test
import kotlin.test.assertEquals

class BillCadenceTest {
    @Test
    fun `every cadence normalises to the same monthly unit price`() {
        // £120 a year, £30 a quarter, £10 a month and £2.31 a week are all £10 a month.
        val monthlyMicros = 10_00 * 1_000_000L
        assertEquals(monthlyMicros, unitPriceMicros(120_00, RecurringCadence.ANNUAL.monthsPerPeriod()))
        assertEquals(monthlyMicros, unitPriceMicros(30_00, RecurringCadence.QUARTERLY.monthsPerPeriod()))
        assertEquals(monthlyMicros, unitPriceMicros(10_00, RecurringCadence.MONTHLY.monthsPerPeriod()))
        assertEquals(
            monthlyMicros.toDouble(),
            unitPriceMicros(231, RecurringCadence.WEEKLY.monthsPerPeriod()).toDouble(),
            2_000_000.0, // £2.31 is the nearest penny to £10 × 12 / 52, so within 0.2p a month
        )
    }
}
