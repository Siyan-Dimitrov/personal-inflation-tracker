package com.siyandimitrov.pocketindex.ui.settings

import com.siyandimitrov.pocketindex.data.local.RecurringCadence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecurringBillsUiStateTest {
    @Test
    fun `monthly total normalises every supported cadence`() {
        val state = RecurringBillsUiState(
            bills = listOf(
                bill(1, 1_200, RecurringCadence.MONTHLY),
                bill(2, 1_200, RecurringCadence.QUARTERLY),
                bill(3, 1_200, RecurringCadence.ANNUAL),
                bill(4, 1_200, RecurringCadence.WEEKLY),
            ),
        )

        assertEquals(6_900, state.monthlyTotalMinor)
    }

    @Test
    fun `GBP input accepts pounds and exact pence`() {
        assertEquals(3_200, "£32".toMinorUnitsOrNull())
        assertEquals(3_245, "32.45".toMinorUnitsOrNull())
        assertEquals(99, ".99".toMinorUnitsOrNull())
    }

    @Test
    fun `GBP input rejects invalid or unsafe prices`() {
        assertNull("0".toMinorUnitsOrNull())
        assertNull("-1".toMinorUnitsOrNull())
        assertNull("1.999".toMinorUnitsOrNull())
        assertNull("not a price".toMinorUnitsOrNull())
        assertNull("999999999999999999999999".toMinorUnitsOrNull())
    }

    private fun bill(
        id: Long,
        priceMinor: Long,
        cadence: RecurringCadence,
    ) = RecurringBillUi(
        recurringItemId = id,
        productId = id,
        name = "Bill $id",
        priceMinor = priceMinor,
        cadence = cadence,
        lastUpdated = "2026-07-30",
    )
}
