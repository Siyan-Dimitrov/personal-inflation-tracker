package com.siyandimitrov.pocketindex.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChainedIndexCalculatorTest {
    @Test
    fun `annual rebases chain multiplicatively and admit a new product`() {
        val category = CategoryId(1)
        val existing = Product(ProductId(1), category, UnitType.COUNT)
        val laterProduct = Product(ProductId(2), category, UnitType.COUNT)
        val observations = listOf(
            observation(1, existing, day = 0, price = 100),
            observation(2, existing, day = 1, price = 100),
            // Prices and purchases used by the second rebase.
            observation(3, existing, day = 100, price = 110),
            observation(4, existing, day = 110, price = 110),
            observation(5, laterProduct, day = 100, price = 300),
            observation(6, laterProduct, day = 110, price = 300),
            // End of the second link.
            observation(7, existing, day = 365, price = 99),
            observation(8, laterProduct, day = 365, price = 360),
        )
        val calculator = PersonalInflationCalculator()
        val originalBasket = calculator.buildFixedBasket(
            products = listOf(existing, laterProduct),
            observations = observations,
            baseWindow = BaseWindow(EpochDay(0), EpochDay(55)),
        )
        val rebasedBasket = calculator.buildFixedBasket(
            products = listOf(existing, laterProduct),
            observations = observations,
            baseWindow = BaseWindow(EpochDay(100), EpochDay(155)),
        )

        assertFalse(laterProduct.id in originalBasket.productIds)
        assertTrue(laterProduct.id in rebasedBasket.productIds)

        val chained = ChainedIndexCalculator.calculate(
            links = listOf(
                BasketLink(
                    basket = originalBasket,
                    fromInclusive = EpochDay(55),
                    toInclusive = EpochDay(155),
                ),
                BasketLink(
                    basket = rebasedBasket,
                    fromInclusive = EpochDay(155),
                    toInclusive = EpochDay(365),
                ),
            ),
        )

        assertEquals(100.0, chained[0].index)
        assertEquals(110.0, chained[1].index, absoluteTolerance = 0.000001)
        // Second local index = (2 * 99 + 2 * 360) / (2 * 110 + 2 * 300) * 100.
        val secondLinkRelative = 918.0 / 820.0
        assertEquals(
            110.0 * secondLinkRelative,
            chained[2].index,
            absoluteTolerance = 0.000001,
        )
    }

    @Test
    fun `simple index relatives chain rather than add`() {
        val category = CategoryId(1)
        val product = Product(ProductId(1), category, UnitType.COUNT)
        val firstBasket = basket(
            product = product,
            baseDay = 0,
            basePrice = 100,
            endDay = 100,
            endPrice = 110,
        )
        val secondBasket = basket(
            product = product,
            baseDay = 100,
            basePrice = 200,
            endDay = 200,
            endPrice = 180,
        )

        val points = ChainedIndexCalculator.calculate(
            listOf(
                BasketLink(firstBasket, EpochDay(0), EpochDay(100)),
                BasketLink(secondBasket, EpochDay(100), EpochDay(200)),
            ),
        )

        assertEquals(110.0, points[1].index, absoluteTolerance = 0.000001)
        assertEquals(99.0, points[2].index, absoluteTolerance = 0.000001)
    }

    private fun basket(
        product: Product,
        baseDay: Long,
        basePrice: Long,
        endDay: Long,
        endPrice: Long,
    ): FixedBasket = PersonalInflationCalculator().buildFixedBasket(
        products = listOf(product),
        observations = listOf(
            observation(1, product, baseDay, basePrice),
            observation(2, product, baseDay, basePrice),
            observation(3, product, endDay, endPrice),
        ),
        baseWindow = BaseWindow(EpochDay(baseDay), EpochDay(baseDay)),
    )

    private fun observation(
        id: Long,
        product: Product,
        day: Long,
        price: Long,
    ) = PriceObservation(
        observationId = id,
        productId = product.id,
        observedOn = EpochDay(day),
        unitPrice = UnitPriceMicros(price),
        purchasedQuantityBaseUnits = 1.0,
        source = ObservationSource.RECEIPT,
    )
}
