package com.siyandimitrov.pocketindex.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnitPriceMicrosTest {
    @Test
    fun `normalises shelf price to integer micros per base unit`() {
        val price = UnitPriceMicros.fromShelfPrice(
            shelfPriceMinor = 120,
            packSizeBaseUnits = 1_000.0,
        )

        assertEquals(120_000L, price.value)
    }

    @Test
    fun `smaller pack at same shelf price exposes shrinkflation`() {
        val oldPack = UnitPriceMicros.fromShelfPrice(120, 1_000.0)
        val newPack = UnitPriceMicros.fromShelfPrice(120, 900.0)

        assertEquals(120_000L, oldPack.value)
        assertEquals(133_333L, newPack.value)
        assertEquals(
            11.111,
            (newPack.value.toDouble() / oldPack.value - 1.0) * 100.0,
            absoluteTolerance = 0.001,
        )
    }

    @Test
    fun `purchase factory records pack evidence and total purchased base units`() {
        val observation = PriceObservation.purchase(
            observationId = 1,
            productId = ProductId(1),
            observedOn = EpochDay(0),
            shelfPriceMinor = 250,
            packSizeBaseUnits = 500.0,
            packsPurchased = 3.0,
        )

        assertEquals(500_000L, observation.unitPrice.value)
        assertEquals(1_500.0, observation.purchasedQuantityBaseUnits)
        assertEquals(250L, observation.shelfPriceMinor)
        assertEquals(500.0, observation.packSizeBaseUnits)
    }

    @Test
    fun `invalid money and quantities are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            UnitPriceMicros.fromShelfPrice(-1, 100.0)
        }
        assertFailsWith<IllegalArgumentException> {
            UnitPriceMicros.fromShelfPrice(100, 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            UnitPriceMicros.fromShelfPrice(100, Double.NaN)
        }
        assertTrue(
            runCatching { UnitPriceMicros.fromShelfPrice(1, 3.0) }.isSuccess,
        )
    }
}
