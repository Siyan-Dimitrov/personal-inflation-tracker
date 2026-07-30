package com.siyandimitrov.pocketindex.extraction

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuleBasedReceiptExtractorTest {
    private val extractor = RuleBasedReceiptExtractor()

    @Test
    fun `parses UK price columns quantities totals and exact raw text`() = runBlocking {
        val milkLine = "WHOLE MILK 2PT                 £1.55"
        val result = extractor.extract(
            ocrResult(
                "TESCO",
                milkLine,
                "2 x BAKED BEANS       £0.75    £1.50",
                "SUBTOTAL                         £3.05",
                "VAT                              £0.61",
                "TOTAL                            £3.66",
            ),
            candidates = listOf(
                ProductCandidate(
                    id = 1,
                    canonicalName = "Whole Milk",
                    packSize = 1_136.0,
                    baseUnit = BaseUnit.VOLUME_ML,
                ),
                ProductCandidate(
                    id = 2,
                    canonicalName = "Baked Beans",
                    packSize = 1.0,
                    baseUnit = BaseUnit.COUNT,
                ),
            ),
        )

        assertEquals("TESCO", result.merchantName)
        assertEquals(2, result.lineItems.size)
        with(result.lineItems[0]) {
            assertEquals(milkLine, rawText)
            assertEquals(155, unitPriceMinor)
            assertEquals(155, lineTotalMinor)
            assertEquals(1_136.0, assertNotNull(packSize).amount)
            assertEquals(BaseUnit.VOLUME_ML, packSize?.baseUnit)
            assertEquals(1L, productMatch?.productId)
        }
        with(result.lineItems[1]) {
            assertEquals(2.0, quantity)
            assertEquals(75, unitPriceMinor)
            assertEquals(150, lineTotalMinor)
            assertEquals(2L, productMatch?.productId)
        }
        assertEquals(ReceiptTotals(305, 61, 366), result.totals)
        assertTrue(result.validation.isValid)
        assertEquals(305, result.validation.calculatedLineItemsMinor)
    }

    @Test
    fun `allows a two pence reconciliation tolerance`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "SHOP",
                "APPLES £1.00",
                "SUBTOTAL £1.02",
                "TOTAL £1.02",
            ),
            emptyList(),
        )

        assertTrue(result.validation.isValid)
        assertEquals(-2, result.validation.lineItemsDifferenceMinor)
    }

    @Test
    fun `flags line item and tax reconciliation failures`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "SHOP",
                "APPLES £1.00",
                "SUBTOTAL £1.10",
                "VAT £0.20",
                "TOTAL £1.20",
            ),
            emptyList(),
        )

        assertFalse(result.validation.isValid)
        assertTrue(
            ValidationIssue.LINE_ITEMS_DO_NOT_EQUAL_SUBTOTAL in result.validation.issues,
        )
        assertTrue(
            ValidationIssue.SUBTOTAL_PLUS_TAX_DOES_NOT_EQUAL_TOTAL in result.validation.issues,
        )
    }

    @Test
    fun `parses receipt style pence and negative discounts`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "CORNER SHOP",
                "APPLE 99p",
                "PROMOTION 0.20-",
                "SUBTOTAL £0.79",
                "TOTAL £0.79",
            ),
            emptyList(),
        )

        assertEquals(listOf(99, -20), result.lineItems.map { it.lineTotalMinor })
        assertTrue(result.validation.isValid)
    }

    @Test
    fun `does not parse card tender change dates or headings as products`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "LOCAL SHOP",
                "ITEM QTY PRICE",
                "30/07/2026 20:12",
                "BREAD £1.25",
                "CARD £1.25",
                "CHANGE £0.00",
                "SUBTOTAL £1.25",
                "TOTAL £1.25",
            ),
            emptyList(),
        )

        assertEquals(listOf("BREAD £1.25"), result.lineItems.map { it.rawText })
    }

    @Test
    fun `inherits candidate pack size but requires review when receipt omits it`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "SHOP",
                "WHOLE MILK £1.55",
                "SUBTOTAL £1.55",
                "TOTAL £1.55",
            ),
            listOf(
                ProductCandidate(
                    id = 1,
                    canonicalName = "Whole Milk",
                    packSize = 1_136.0,
                    baseUnit = BaseUnit.VOLUME_ML,
                ),
            ),
        )

        val item = result.lineItems.single()
        assertEquals(1_136.0, item.packSize?.amount)
        assertTrue(LineItemIssue.MISSING_PACK_SIZE in item.issues)
        assertTrue(result.needsReview)
    }

    @Test
    fun `flags unmatched products for review without changing raw text`() = runBlocking {
        val rawLine = "UNKNOWN SPECIAL £4.20"
        val result = extractor.extract(
            ocrResult(
                "SHOP",
                rawLine,
                "SUBTOTAL £4.20",
                "TOTAL £4.20",
            ),
            emptyList(),
        )

        val item = result.lineItems.single()
        assertEquals(rawLine, item.rawText)
        assertTrue(LineItemIssue.NO_PRODUCT_MATCH in item.issues)
        assertTrue(result.needsReview)
    }

    @Test
    fun `parses metric imperial and count pack sizes into base units`() {
        assertPack("FLOUR 500G", 500.0, BaseUnit.MASS_G)
        assertPack("RICE 1.5KG", 1_500.0, BaseUnit.MASS_G)
        assertPack("MILK 2PT", 1_136.0, BaseUnit.VOLUME_ML)
        assertPack("WATER 1.5L", 1_500.0, BaseUnit.VOLUME_ML)
        assertPack("COLA 6 x 330ml", 1_980.0, BaseUnit.VOLUME_ML)
        assertPack("EGGS 12PK", 12.0, BaseUnit.COUNT)
        assertPack("YOGHURT 2 x 500g", 1_000.0, BaseUnit.MASS_G)
    }

    private fun assertPack(text: String, amount: Double, unit: BaseUnit) {
        val pack = assertNotNull(extractor.parsePackSize(text))
        assertEquals(amount, pack.amount)
        assertEquals(unit, pack.baseUnit)
    }

    private fun ocrResult(vararg lines: String): OcrResult =
        OcrResult(
            text = lines.joinToString("\n"),
            lines = lines.map { OcrLine(it) },
        )
}
