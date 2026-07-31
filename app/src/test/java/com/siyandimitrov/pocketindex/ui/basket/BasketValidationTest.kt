package com.siyandimitrov.pocketindex.ui.basket

import com.siyandimitrov.pocketindex.data.repository.decodeAliases
import com.siyandimitrov.pocketindex.data.repository.encodeAliases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BasketValidationTest {
    @Test
    fun `manual observation accepts valid date price and pack`() {
        assertNull(
            validateObservationInput(
                observedAt = "2026-07-31",
                priceText = "£1.25",
                packSizeText = "500",
            ),
        )
    }

    @Test
    fun `manual observation rejects impossible date and non-positive values`() {
        assertNotNull(validateObservationInput("2026-02-30", "1.25", "500"))
        assertNotNull(validateObservationInput("2026-02-28", "0", "500"))
        assertNotNull(validateObservationInput("2026-02-28", "1.25", "-1"))
    }

    @Test
    fun `product validation permits an unknown typical pack but requires identity`() {
        assertNull(validateProductInput("Oats", 4, ""))
        assertNotNull(validateProductInput(" ", 4, "500"))
        assertNotNull(validateProductInput("Oats", null, "500"))
        assertNotNull(validateProductInput("Oats", 4, "0"))
    }

    @Test
    fun `aliases round trip escaped text and remove case-insensitive duplicates`() {
        val encoded = encodeAliases(listOf("Receipt \"Oats\"", "receipt \"oats\"", "Line\nTwo"))

        assertEquals(
            listOf("Receipt \"Oats\"", "Line\nTwo"),
            decodeAliases(encoded),
        )
    }
}
