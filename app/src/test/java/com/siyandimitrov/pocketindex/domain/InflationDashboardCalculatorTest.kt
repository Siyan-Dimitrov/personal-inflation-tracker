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

    private fun input(
        observations: List<PriceObservation>,
        overrides: Map<CategoryId, Double> = emptyMap(),
    ) = InflationDashboardInput(
        products = listOf(
            Product(PRODUCT_ONE, CATEGORY_ONE, UnitType.COUNT),
            Product(PRODUCT_TWO, CATEGORY_TWO, UnitType.COUNT),
        ),
        observations = observations,
        productNames = mapOf(PRODUCT_ONE to "Milk", PRODUCT_TWO to "Bread"),
        categoryNames = mapOf(CATEGORY_ONE to "Groceries", CATEGORY_TWO to "Household"),
        categoryWeightOverrides = overrides,
    )

    private fun observation(
        id: Long,
        productId: ProductId,
        day: Long,
        price: Long,
    ) = PriceObservation.purchase(
        observationId = id,
        productId = productId,
        observedOn = EpochDay(day),
        shelfPriceMinor = price,
        packSizeBaseUnits = 1.0,
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
        val CATEGORY_ONE = CategoryId(1)
        val CATEGORY_TWO = CategoryId(2)
    }
}
