package com.siyandimitrov.pocketindex.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyProductMatcherTest {
    @Test
    fun `normaliser removes prices pack sizes and punctuation`() {
        val normalised =
            ReceiptTextNormaliser.normalise("TESCO British Whole-Milk 2PT  £1.55")

        assertEquals("tesco british whole milk", normalised)
    }

    @Test
    fun `normalised edit similarity handles common OCR substitutions`() {
        val similarity =
            StringSimilarity.normalisedEditSimilarity("whole milk", "who1e milk")

        assertTrue(similarity > 0.85)
        assertEquals(1.0, StringSimilarity.normalisedEditSimilarity("milk", "milk"))
        assertEquals(0.0, StringSimilarity.normalisedEditSimilarity("", "milk"))
    }

    @Test
    fun `matcher considers aliases and ranks the strongest candidate first`() {
        val candidates = listOf(
            ProductCandidate(1, "Whole Milk", aliases = listOf("British Whole Milk")),
            ProductCandidate(2, "White Bread", aliases = listOf("Medium Sliced White")),
        )

        val ranked = FuzzyProductMatcher().rank("BRIT1SH WHOLE MILK 2PT £1.55", candidates)

        assertEquals(1L, ranked.first().productId)
        assertTrue(ranked.first().confidence > 0.80)
    }

    @Test
    fun `best match rejects unrelated candidates`() {
        val match = FuzzyProductMatcher().bestMatch(
            rawDescription = "WASHING UP LIQUID",
            candidates = listOf(ProductCandidate(1, "Whole Milk")),
        )

        assertNull(match)
    }
}

