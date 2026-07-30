package com.siyandimitrov.pocketindex.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class GoldenIndexFixtureTest {
    @Test
    fun `hand calculated category weights and headline index match`() {
        val food = CategoryId(1)
        val utilities = CategoryId(2)
        val milk = Product(ProductId(1), food, UnitType.VOLUME_ML)
        val bread = Product(ProductId(2), food, UnitType.COUNT)
        val broadband = Product(ProductId(3), utilities, UnitType.SERVICE)

        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk, bread, broadband),
            observations = listOf(
                // Base expenditure: milk 2,000 * 100 = 200,000 micros.
                observation(1, milk, 0, price = 100, quantity = 1_000.0),
                observation(2, milk, 7, price = 100, quantity = 1_000.0),
                // Base expenditure: bread 2 * 100,000 = 200,000 micros.
                observation(3, bread, 0, price = 100_000, quantity = 1.0),
                observation(4, bread, 7, price = 100_000, quantity = 1.0),
                // Base expenditure: utilities 1 * 600,000 = 600,000 micros.
                observation(
                    5,
                    broadband,
                    0,
                    price = 600_000,
                    quantity = 1.0,
                    source = ObservationSource.BILL,
                ),
                // Current food: 2,000 * 110 + 2 * 120,000 = 460,000 => 115.
                observation(6, bread, 50, price = 120_000, quantity = 1.0),
                observation(7, milk, 60, price = 110, quantity = 1_000.0),
                // Current utilities: 1 * 660,000 = 660,000 => 110.
                observation(
                    8,
                    broadband,
                    30,
                    price = 660_000,
                    quantity = 1.0,
                    source = ObservationSource.BILL,
                ),
            ),
            baseWindow = BaseWindow(EpochDay(0), EpochDay(7)),
        )

        val base = basket.snapshot(EpochDay(7))
        assertEquals(100.0, base.headlineIndex, absoluteTolerance = 0.000001)
        assertEquals(0.4, basket.categoryWeights.getValue(food), 0.000001)
        assertEquals(0.6, basket.categoryWeights.getValue(utilities), 0.000001)

        val current = basket.snapshot(EpochDay(60))
        assertEquals(
            115.0,
            current.categories.single { it.categoryId == food }.index,
            absoluteTolerance = 0.000001,
        )
        assertEquals(
            110.0,
            current.categories.single { it.categoryId == utilities }.index,
            absoluteTolerance = 0.000001,
        )
        // 0.4 * 115 + 0.6 * 110 = 112.
        assertEquals(112.0, current.headlineIndex, absoluteTolerance = 0.000001)
        assertEquals(100.0, current.freshCoveragePercent)
    }

    private fun observation(
        id: Long,
        product: Product,
        day: Long,
        price: Long,
        quantity: Double,
        source: ObservationSource = ObservationSource.RECEIPT,
    ) = PriceObservation(
        observationId = id,
        productId = product.id,
        observedOn = EpochDay(day),
        unitPrice = UnitPriceMicros(price),
        purchasedQuantityBaseUnits = quantity,
        source = source,
    )
}
