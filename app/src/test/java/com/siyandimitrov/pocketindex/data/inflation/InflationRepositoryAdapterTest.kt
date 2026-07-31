package com.siyandimitrov.pocketindex.data.inflation

import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.domain.EpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InflationRepositoryAdapterTest {
    @Test
    fun `strict ISO dates map to epoch days`() {
        assertEquals(EpochDay(0), parseIsoEpochDay("1970-01-01"))
        assertEquals(EpochDay(20_453), parseIsoEpochDay("2025-12-31"))
    }

    @Test
    fun `invalid persisted date is rejected for calculation error state`() {
        assertFailsWith<IllegalArgumentException> {
            parseIsoEpochDay("2026-02-30")
        }
    }

    @Test
    fun `receipt quantity contributes every purchased pack to base weight`() {
        assertEquals(
            1_500.0,
            purchasedQuantityBaseUnits(
                source = ObservationSource.RECEIPT,
                packSize = 500.0,
                receiptQuantity = 3.0,
            ),
        )
        assertEquals(
            500.0,
            purchasedQuantityBaseUnits(
                source = ObservationSource.MANUAL,
                packSize = 500.0,
                receiptQuantity = null,
            ),
        )
        assertEquals(
            1.0,
            purchasedQuantityBaseUnits(
                source = ObservationSource.BILL,
                packSize = 500.0,
                receiptQuantity = null,
            ),
        )
    }
}
