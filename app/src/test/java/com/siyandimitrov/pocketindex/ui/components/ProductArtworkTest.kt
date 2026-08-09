package com.siyandimitrov.pocketindex.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ProductArtworkTest {
    @Test
    fun `products get artwork from their name keywords`() {
        assertEquals("🥛", productEmoji("Whole Milk"))
        assertEquals("🍝", productEmoji("Fusilli Pasta 500g"))
        assertEquals("🍅", productEmoji("Vine Tomatoes"))
        assertEquals("🫒", productEmoji("KS Olive Oil 2L"))
        assertEquals("⚡", productEmoji("Electricity"))
    }

    @Test
    fun `specific names outrank the generic words they contain`() {
        // "Milk Chocolate" is chocolate, not milk; "Watermelon" is not a melon.
        assertEquals("🍫", productEmoji("Milk Chocolate Buttons"))
        assertEquals("🥥", productEmoji("Coconut Milk 400ml"))
        assertEquals("🍉", productEmoji("Watermelon Each"))
    }

    @Test
    fun `unknown products fall back to category then to a parcel`() {
        assertEquals("🛒", productEmoji("Mystery Item", categoryName = "Groceries"))
        assertEquals("🚗", productEmoji("Zone 3 Pass", categoryName = "Transport"))
        assertEquals("📦", productEmoji("Mystery Item"))
    }
}
