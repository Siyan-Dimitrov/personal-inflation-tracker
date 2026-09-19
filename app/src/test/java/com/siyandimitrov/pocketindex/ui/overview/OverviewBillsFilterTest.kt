package com.siyandimitrov.pocketindex.ui.overview

import com.siyandimitrov.pocketindex.domain.CategoryId
import com.siyandimitrov.pocketindex.domain.EpochDay
import com.siyandimitrov.pocketindex.domain.InflationDashboardInput
import com.siyandimitrov.pocketindex.domain.PriceObservation
import com.siyandimitrov.pocketindex.domain.Product
import com.siyandimitrov.pocketindex.domain.ProductId
import com.siyandimitrov.pocketindex.domain.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewBillsFilterTest {
    private val milk = Product(ProductId(1), CategoryId(1), UnitType.VOLUME_ML)
    private val energy = Product(ProductId(2), CategoryId(2), UnitType.SERVICE)

    private val input = InflationDashboardInput(
        products = listOf(milk, energy),
        observations = listOf(
            PriceObservation.purchase(
                observationId = 1,
                productId = milk.id,
                observedOn = EpochDay(0),
                shelfPriceMinor = 120,
                packSizeBaseUnits = 1000.0,
            ),
            PriceObservation.bill(
                observationId = 2,
                productId = energy.id,
                observedOn = EpochDay(0),
                periodPriceMinor = 9_000,
            ),
        ),
        productNames = mapOf(milk.id to "Milk", energy.id to "Energy"),
        categoryNames = mapOf(CategoryId(1) to "Groceries", CategoryId(2) to "Household bills"),
    )

    @Test
    fun `excluding bills drops service products and their observations`() {
        val filtered = input.withoutBills()

        assertEquals(listOf(milk), filtered.products)
        assertEquals(listOf(1L), filtered.observations.map { it.observationId })
        // Names and weights are untouched; only the basket inputs shrink.
        assertEquals(input.productNames, filtered.productNames)
        assertEquals(input.categoryNames, filtered.categoryNames)
    }
}
