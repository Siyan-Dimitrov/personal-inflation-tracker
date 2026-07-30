package com.siyandimitrov.pocketindex.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalInflationCalculatorTest {
    private val food = CategoryId(1)
    private val utilities = CategoryId(2)
    private val milk = Product(ProductId(1), food, UnitType.VOLUME_ML)
    private val bread = Product(ProductId(2), food, UnitType.COUNT)
    private val broadband = Product(ProductId(3), utilities, UnitType.SERVICE)

    @Test
    fun `price resolution carries forward latest observation and ignores the future`() {
        val history = PriceHistory(
            listOf(
                observation(1, milk, day = 0, unitPrice = 100),
                observation(2, milk, day = 10, unitPrice = 125),
                observation(3, milk, day = 40, unitPrice = 200),
            ),
        )

        assertEquals(null, history.priceAt(EpochDay(-1)))
        assertEquals(100.0, history.priceAt(EpochDay(9))!!.meanUnitPriceMicros)
        assertEquals(125.0, history.priceAt(EpochDay(10))!!.meanUnitPriceMicros)
        assertEquals(125.0, history.priceAt(EpochDay(39))!!.meanUnitPriceMicros)
        assertEquals(200.0, history.priceAt(EpochDay(40))!!.meanUnitPriceMicros)
    }

    @Test
    fun `same-day correction with higher observation id wins`() {
        val history = PriceHistory(
            listOf(
                observation(10, milk, day = 0, unitPrice = 100),
                observation(11, milk, day = 0, unitPrice = 120),
            ),
        )

        assertEquals(120.0, history.priceAt(EpochDay(0))!!.meanUnitPriceMicros)
    }

    @Test
    fun `merchant prices carry forward independently and are averaged`() {
        val merchantA = MerchantId(1)
        val merchantB = MerchantId(2)
        val observations = listOf(
            observation(1, milk, day = 0, unitPrice = 100, merchant = merchantA),
            observation(2, milk, day = 0, unitPrice = 200, merchant = merchantB),
            observation(3, milk, day = 10, unitPrice = 120, merchant = merchantA),
            observation(4, milk, day = 20, unitPrice = 220, merchant = merchantB),
        )
        val history = PriceHistory(observations)

        assertEquals(150.0, history.priceAt(EpochDay(0))!!.meanUnitPriceMicros)
        assertEquals(160.0, history.priceAt(EpochDay(10))!!.meanUnitPriceMicros)
        assertEquals(170.0, history.priceAt(EpochDay(20))!!.meanUnitPriceMicros)

        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk),
            observations = observations,
            baseWindow = BaseWindow(EpochDay(0), EpochDay(0)),
        )
        assertEquals(
            113.333333,
            basket.snapshot(EpochDay(20)).headlineIndex,
            absoluteTolerance = 0.000001,
        )
    }

    @Test
    fun `grocery becomes stale only after ninety days while service stays fresh`() {
        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk, broadband),
            observations = listOf(
                observation(1, milk, day = 0, unitPrice = 100),
                observation(2, milk, day = 1, unitPrice = 100),
                observation(
                    id = 3,
                    product = broadband,
                    day = 0,
                    unitPrice = 100,
                    source = ObservationSource.BILL,
                ),
            ),
            baseWindow = BaseWindow(EpochDay(0), EpochDay(1)),
        )

        val atNinetyDays = basket.snapshot(EpochDay(91))
        assertFalse(atNinetyDays.products.single { it.productId == milk.id }.isStale)
        assertEquals(100.0, atNinetyDays.freshCoveragePercent)

        val afterNinetyDays = basket.snapshot(EpochDay(92))
        assertTrue(afterNinetyDays.products.single { it.productId == milk.id }.isStale)
        assertFalse(
            afterNinetyDays.products.single { it.productId == broadband.id }.isStale,
        )
        // Milk base expenditure is 200 and broadband is 100.
        assertEquals(
            100.0 / 3.0,
            afterNinetyDays.freshCoveragePercent,
            absoluteTolerance = 0.000001,
        )
    }

    @Test
    fun `grocery needs two base observations but a bill enters after one`() {
        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk, broadband),
            observations = listOf(
                observation(1, milk, day = 0, unitPrice = 100),
                observation(
                    id = 2,
                    product = broadband,
                    day = 0,
                    unitPrice = 500,
                    source = ObservationSource.BILL,
                ),
            ),
            baseWindow = BaseWindow(EpochDay(0), EpochDay(7)),
        )

        assertEquals(setOf(broadband.id), basket.productIds)
    }

    @Test
    fun `product first seen after base window is excluded from fixed basket`() {
        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk, bread),
            observations = listOf(
                observation(1, milk, day = 0, unitPrice = 100),
                observation(2, milk, day = 7, unitPrice = 100),
                observation(3, bread, day = 10, unitPrice = 1_000),
                observation(4, bread, day = 11, unitPrice = 1_000),
                observation(5, bread, day = 20, unitPrice = 5_000),
            ),
            baseWindow = BaseWindow(EpochDay(0), EpochDay(7)),
        )

        assertEquals(setOf(milk.id), basket.productIds)
        assertEquals(100.0, basket.snapshot(EpochDay(20)).headlineIndex)
    }

    @Test
    fun `first eight weeks are selected by default`() {
        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk, bread),
            observations = listOf(
                observation(1, milk, day = 100, unitPrice = 100),
                observation(2, milk, day = 155, unitPrice = 100),
                observation(3, bread, day = 156, unitPrice = 1_000),
                observation(4, bread, day = 157, unitPrice = 1_000),
            ),
        )

        assertEquals(EpochDay(100), basket.baseWindow.startInclusive)
        assertEquals(EpochDay(155), basket.baseWindow.endInclusive)
        assertEquals(setOf(milk.id), basket.productIds)
    }

    @Test
    fun `partial category override reserves remaining weight for automatic categories`() {
        val basket = PersonalInflationCalculator().buildFixedBasket(
            products = listOf(milk, broadband),
            observations = listOf(
                observation(1, milk, day = 0, unitPrice = 100),
                observation(2, milk, day = 1, unitPrice = 100),
                observation(
                    3,
                    broadband,
                    day = 0,
                    unitPrice = 100,
                    source = ObservationSource.BILL,
                ),
            ),
            baseWindow = BaseWindow(EpochDay(0), EpochDay(1)),
            categoryWeightOverrides = mapOf(food to 0.25),
        )

        assertEquals(0.25, basket.categoryWeights.getValue(food))
        assertEquals(0.75, basket.categoryWeights.getValue(utilities))
    }

    private fun observation(
        id: Long,
        product: Product,
        day: Long,
        unitPrice: Long,
        quantity: Double = 1.0,
        merchant: MerchantId? = null,
        source: ObservationSource = ObservationSource.RECEIPT,
    ) = PriceObservation(
        observationId = id,
        productId = product.id,
        observedOn = EpochDay(day),
        unitPrice = UnitPriceMicros(unitPrice),
        purchasedQuantityBaseUnits = quantity,
        merchantId = merchant,
        source = source,
    )
}
