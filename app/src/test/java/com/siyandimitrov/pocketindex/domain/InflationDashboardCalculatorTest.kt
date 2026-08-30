package com.siyandimitrov.pocketindex.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InflationDashboardCalculatorTest {
    @Test
    fun `reports base basket construction before configured window ends`() {
        val input = input(
            observations = listOf(observation(1, PRODUCT_ONE, day = 10, price = 100)),
        )

        val result = InflationDashboardCalculator.calculate(
            input = input,
            asOf = EpochDay(20),
            configuration = IndexConfiguration(baseWindowDays = 56),
        )

        val building = assertIs<InflationDashboardCalculation.BuildingBaseBasket>(result)
        assertEquals(EpochDay(65), building.baseWindowEndsOn)
        assertEquals(0, building.eligibleProductCount)
    }

    @Test
    fun `calculates weighted category and product contributions with stale coverage`() {
        val input = input(
            observations = listOf(
                observation(1, PRODUCT_ONE, 0, 100),
                observation(2, PRODUCT_ONE, 1, 100),
                observation(3, PRODUCT_ONE, 32, 120),
                bill(4, PRODUCT_TWO, 0, 200),
            ),
        )

        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input,
                asOf = EpochDay(32),
                configuration = IndexConfiguration(baseWindowDays = 2, staleAfterDays = 10),
            ),
        )

        assertEquals(110.0, ready.currentFixedIndex, 1e-8)
        assertEquals(50.0, ready.freshCoveragePercent, 1e-8)
        assertEquals(listOf("Bread"), ready.staleProducts.map { it.name })
        val headline = assertNotNull(ready.headlineRate)
        assertEquals(
            headline.percent,
            ready.categories.sumOf { it.contributionPercentagePoints },
            1e-8,
        )
        assertEquals(
            headline.percent,
            ready.productContributions.sumOf { it.contributionPercentagePoints },
            1e-8,
        )
        assertTrue(ready.categories.first { it.name == "Groceries" }.changePercent > 0.0)
        assertNull(ready.chainedSeries)
    }

    @Test
    fun `category override changes headline weight`() {
        val input = input(
            observations = listOf(
                observation(1, PRODUCT_ONE, 0, 100),
                observation(2, PRODUCT_ONE, 1, 100),
                observation(3, PRODUCT_ONE, 32, 120),
                bill(4, PRODUCT_TWO, 0, 200),
            ),
            overrides = mapOf(CATEGORY_ONE to 0.25),
        )

        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input,
                asOf = EpochDay(32),
                configuration = IndexConfiguration(baseWindowDays = 2),
            ),
        )

        assertEquals(105.0, ready.currentFixedIndex, 1e-8)
        assertEquals(0.25, ready.categories.first { it.categoryId == CATEGORY_ONE }.weight, 1e-8)
        assertEquals(0.75, ready.categories.first { it.categoryId == CATEGORY_TWO }.weight, 1e-8)
    }

    @Test
    fun `gives each calendar month a single point when the base window ends mid-month`() {
        val input = input(
            observations = listOf(
                observation(1, PRODUCT_ONE, 0, 100),
                observation(2, PRODUCT_ONE, 1, 100),
                bill(3, PRODUCT_TWO, 0, 200),
            ),
        )

        // The base window closes on 1970-01-20, eleven days before the January month end.
        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input,
                asOf = EpochDay(100),
                configuration = IndexConfiguration(baseWindowDays = 20),
            ),
        )

        val months = ready.fixedSeries.map { it.asOf.month() }
        assertEquals(listOf("1970-01", "1970-02", "1970-03", "1970-04"), months)
    }

    @Test
    fun `provides chained series after a viable annual rebase`() {
        val input = input(
            observations = listOf(
                observation(1, PRODUCT_ONE, 0, 100),
                observation(2, PRODUCT_ONE, 1, 100),
                bill(3, PRODUCT_TWO, 0, 200),
                observation(4, PRODUCT_ONE, 365, 130),
                observation(5, PRODUCT_ONE, 366, 130),
                bill(6, PRODUCT_TWO, 365, 220),
                observation(7, PRODUCT_ONE, 400, 140),
                bill(8, PRODUCT_TWO, 400, 230),
            ),
        )

        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input,
                asOf = EpochDay(400),
                configuration = IndexConfiguration(baseWindowDays = 2),
            ),
        )

        assertTrue(assertNotNull(ready.chainedSeries).size > 12)
        assertNotNull(ready.fixedToChainedGapPercent)
    }

    @Test
    fun `builds a separate index per shop from that shop's prices alone`() {
        // Three products at Tesco in the base window, each 20% dearer a month on; the same
        // products at Lidl held flat. The headline averages the shops; the cards must not.
        val observations = buildList {
            var id = 1L
            listOf(PRODUCT_ONE, PRODUCT_TWO, PRODUCT_THREE).forEach { product ->
                add(observation(id++, product, day = 0, price = 100, merchant = TESCO))
                add(observation(id++, product, day = 1, price = 100, merchant = TESCO))
                add(observation(id++, product, day = 32, price = 120, merchant = TESCO))
                add(observation(id++, product, day = 0, price = 100, merchant = LIDL))
                add(observation(id++, product, day = 1, price = 100, merchant = LIDL))
                add(observation(id++, product, day = 32, price = 100, merchant = LIDL))
            }
        }

        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input(observations),
                asOf = EpochDay(32),
                configuration = IndexConfiguration(baseWindowDays = 2),
            ),
        )

        assertEquals(110.0, ready.currentFixedIndex, 1e-8)
        assertEquals(listOf("Lidl", "Tesco"), ready.merchants.map { it.name })
        val tesco = ready.merchants.first { it.name == "Tesco" }
        assertEquals(TESCO, tesco.merchantId)
        assertEquals(9, tesco.observationCount)
        assertEquals(3, tesco.basketProductCount)
        assertEquals(120.0, assertNotNull(tesco.series).last().index, 1e-8)
        assertEquals(ready.fixedSeries.map { it.asOf }, tesco.series.map { it.asOf })
        assertEquals(100.0, assertNotNull(ready.merchants.first { it.name == "Lidl" }.series).last().index, 1e-8)
    }

    @Test
    fun `a shop with too few basket products is listed without a series`() {
        val observations = listOf(
            observation(1, PRODUCT_ONE, day = 0, price = 100, merchant = TESCO),
            observation(2, PRODUCT_ONE, day = 1, price = 100, merchant = TESCO),
            observation(3, PRODUCT_TWO, day = 0, price = 100, merchant = TESCO),
            observation(4, PRODUCT_TWO, day = 1, price = 100, merchant = TESCO),
            observation(5, PRODUCT_ONE, day = 32, price = 150, merchant = TESCO),
            // A single observation at Lidl never qualifies a product.
            observation(6, PRODUCT_THREE, day = 32, price = 100, merchant = LIDL),
            // Unattributed prices count for the headline only.
            observation(7, PRODUCT_THREE, day = 0, price = 100),
            observation(8, PRODUCT_THREE, day = 1, price = 100),
        )

        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input(observations),
                asOf = EpochDay(32),
                configuration = IndexConfiguration(baseWindowDays = 2),
            ),
        )

        assertEquals(listOf("Tesco", "Lidl"), ready.merchants.map { it.name })
        val tesco = ready.merchants.first()
        assertEquals(2, tesco.basketProductCount)
        assertNull(tesco.series)
        assertEquals(0, ready.merchants.last().basketProductCount)
        assertNull(ready.merchants.last().series)
    }

    @Test
    fun `an unnamed shop falls back to its id`() {
        val observations = listOf(PRODUCT_ONE, PRODUCT_TWO, PRODUCT_THREE).flatMapIndexed { index, product ->
            val base = index * 10L
            listOf(
                observation(base + 1, product, day = 0, price = 100, merchant = MerchantId(99)),
                observation(base + 2, product, day = 1, price = 100, merchant = MerchantId(99)),
            )
        }

        val ready = assertIs<InflationDashboardCalculation.Ready>(
            InflationDashboardCalculator.calculate(
                input = input(observations),
                asOf = EpochDay(32),
                configuration = IndexConfiguration(baseWindowDays = 2),
            ),
        )

        assertEquals("Shop 99", ready.merchants.single().name)
        assertNotNull(ready.merchants.single().series)
    }

    private fun input(
        observations: List<PriceObservation>,
        overrides: Map<CategoryId, Double> = emptyMap(),
    ) = InflationDashboardInput(
        products = listOf(
            Product(PRODUCT_ONE, CATEGORY_ONE, UnitType.COUNT),
            Product(PRODUCT_TWO, CATEGORY_TWO, UnitType.COUNT),
            Product(PRODUCT_THREE, CATEGORY_ONE, UnitType.COUNT),
        ),
        observations = observations,
        productNames = mapOf(PRODUCT_ONE to "Milk", PRODUCT_TWO to "Bread", PRODUCT_THREE to "Eggs"),
        categoryNames = mapOf(CATEGORY_ONE to "Groceries", CATEGORY_TWO to "Household"),
        categoryWeightOverrides = overrides,
        merchantNames = mapOf(TESCO to "Tesco", LIDL to "Lidl"),
    )

    private fun observation(
        id: Long,
        productId: ProductId,
        day: Long,
        price: Long,
        merchant: MerchantId? = null,
    ) = PriceObservation.purchase(
        observationId = id,
        productId = productId,
        observedOn = EpochDay(day),
        shelfPriceMinor = price,
        packSizeBaseUnits = 1.0,
        merchantId = merchant,
    )

    private fun bill(
        id: Long,
        productId: ProductId,
        day: Long,
        price: Long,
    ) = PriceObservation.bill(
        observationId = id,
        productId = productId,
        observedOn = EpochDay(day),
        periodPriceMinor = price,
    )

    private fun EpochDay.month(): String = SimpleDateFormat("yyyy-MM", Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(Math.multiplyExact(value, 86_400_000L)))

    private companion object {
        val PRODUCT_ONE = ProductId(1)
        val PRODUCT_TWO = ProductId(2)
        val PRODUCT_THREE = ProductId(3)
        val CATEGORY_ONE = CategoryId(1)
        val CATEGORY_TWO = CategoryId(2)
        val TESCO = MerchantId(1)
        val LIDL = MerchantId(2)
    }
}
