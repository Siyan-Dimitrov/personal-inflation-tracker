package com.siyandimitrov.pocketindex.extraction

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `reads a Lidl receipt whose prices carry a trailing VAT class letter`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "Lidl",
                "ABE-Hutcheon Street",
                "VAT NO. GB350396892",
                "Dark Chocolate 85% 5 x £2.49 12.45 B",
                "15% off coupon -1.85",
                "Cucumber 0082231 3 x £0.99 2.97 A",
                "15% off coupon -0.45",
                "Gouda Slices 2.69 A",
                "15% off coupon -0.40",
                "Silesian Sausages 3.99 A",
                "Greek Salad Cheese 5 x £0.85 4.25 A",
                "Frankfurters 1.99 A",
                "Carli Green Pointed 0082569 1.99 A",
                "Vine Tomatoes 0083819 1.99 A",
                "Nectarines 1kg 0080388 2.39 A",
                "Price Cut -0.60",
                "Piel de Sapo Melon 0080581 1.49 A",
                "Price Cut -0.20",
                "TOTAL 32.70",
                "CARD 32.70",
                "Amount £32.70",
                "TOTAL DISCOUNT 3.50",
                "A 0 % 22.10 0.00",
                "B 20 % 10.60 1.77",
                "You saved £2.70 with Lidl Plus",
            ),
            emptyList(),
        )

        assertEquals(15, result.lineItems.size)
        assertEquals(3_270, result.totals.totalMinor)
        // Every line the receipt printed, and nothing from the VAT breakdown or payment block.
        assertEquals(3_270, result.validation.calculatedLineItemsMinor)
        with(result.lineItems.first()) {
            assertEquals("Dark Chocolate 85%", description)
            assertEquals(5.0, quantity)
            assertEquals(249, unitPriceMinor)
            assertEquals(1_245, lineTotalMinor)
            assertFalse(LineItemIssue.AMBIGUOUS_PRICE in issues)
        }
        assertEquals(
            listOf(-185, -45, -40, -60, -20),
            result.lineItems.map { it.lineTotalMinor }.filter { it < 0 },
        )
        assertEquals(1_000.0, result.lineItems.first { "Nectarines" in it.rawText }.packSize?.amount)
    }

    @Test
    fun `reads a Tesco receipt with Clubcard price cuts and weighted produce`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "TESCO",
                "CRAVENDALE WHOLE MILK 2L 2.85",
                "Clubcard Price -0.55",
                "BANANAS LOOSE",
                "0.912 kg @ £0.90/kg 0.82",
                "TOTAL 3.12",
                "CARD 3.12",
                "CLUBCARD SAVINGS 0.55",
            ),
            emptyList(),
        )

        assertEquals("TESCO", result.merchantName)
        assertEquals(312, result.totals.totalMinor)
        // The savings summary is dropped; the per-line Clubcard cut is kept as evidence.
        assertEquals(listOf(285, -55, 82), result.lineItems.map { it.lineTotalMinor })
        assertEquals(312, result.validation.calculatedLineItemsMinor)
        // The Clubcard cut is a promotion, so it carries no product-match review noise.
        assertTrue(result.lineItems[1].issues.isEmpty())
        // Weighted produce is not yet understood: the amount survives for reconciliation but
        // the description degrades to the weight fragment and the line asks for review.
        assertTrue(LineItemIssue.AMBIGUOUS_PRICE in result.lineItems[2].issues)
    }

    @Test
    fun `reads a Sainsburys receipt whose total is labelled balance due`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "SAINSBURY'S",
                "JS SEMI SKIMMED MILK 2.27L 1.65",
                "JS OLIVE SPREAD 500G 1.75",
                "Nectar Price Saving -0.30",
                "6 BALANCE DUE 3.10",
                "VISA 3.10",
            ),
            emptyList(),
        )

        assertEquals("SAINSBURY'S", result.merchantName)
        assertEquals(310, result.totals.totalMinor)
        assertEquals(listOf(165, 175, -30), result.lineItems.map { it.lineTotalMinor })
        assertEquals(310, result.validation.calculatedLineItemsMinor)
        assertTrue(result.lineItems[2].issues.isEmpty())
        // "2.27" in the pack size reads as a price column, so the line asks for review.
        assertTrue(LineItemIssue.AMBIGUOUS_PRICE in result.lineItems[0].issues)
    }

    @Test
    fun `reads a Morrisons receipt with More Card price cuts`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "MORRISONS",
                "BUTTER UNSALTED 250G 1.99",
                "More Card Price -0.40",
                "TOTAL TO PAY 1.59",
                "SAVINGS WITH YOUR MORE CARD 0.40",
            ),
            emptyList(),
        )

        assertEquals("MORRISONS", result.merchantName)
        assertEquals(159, result.totals.totalMinor)
        // "More Card Price" mentions a payment word but is a price cut, not a payment line.
        assertEquals(listOf(199, -40), result.lineItems.map { it.lineTotalMinor })
        assertEquals(159, result.validation.calculatedLineItemsMinor)
        assertTrue(result.lineItems[1].issues.isEmpty())
    }

    @Test
    fun `reads a Costco receipt with item codes and VAT added after the subtotal`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "COSTCO WHOLESALE",
                "Member 111222333444",
                "96716 KS OLIVE OIL 2L 8.99 E",
                "133774 ORGANIC EGGS 24 3.49 Z",
                "294721 / 96716 TPD 1.50-",
                "SUBTOTAL 10.98",
                "VAT 1.80",
                "**** TOTAL 12.78",
                "ITEMS SOLD 3",
            ),
            emptyList(),
        )

        assertEquals("COSTCO WHOLESALE", result.merchantName)
        assertEquals(ReceiptTotals(1_098, 180, 1_278), result.totals)
        assertEquals(listOf(899, 349, -150), result.lineItems.map { it.lineTotalMinor })
        // Costco prints ex-VAT prices, so subtotal plus VAT reconciles against the total.
        assertTrue(result.validation.isValid)
        // Leading item codes stay in the description for now, which weakens product matching.
        assertEquals("96716 KS OLIVE OIL 2L", result.lineItems[0].description)
        assertEquals(2_000.0, result.lineItems[0].packSize?.amount)
        // The TPD markdown code is not yet recognised as a promotion, so it asks for review;
        // its negative amount still keeps it out of the index by default.
        assertTrue(LineItemIssue.NO_PRODUCT_MATCH in result.lineItems[2].issues)
    }

    @Test
    fun `keeps promotional lines as evidence without matching them to products`() = runBlocking {
        val result = extractor.extract(
            ocrResult(
                "SHOP",
                "Price Cut -0.60",
                "TOTAL -0.60",
            ),
            listOf(ProductCandidate(id = 1, canonicalName = "Price Cut Nectarines")),
        )

        val item = result.lineItems.single()
        assertEquals(-60, item.lineTotalMinor)
        assertNull(item.productMatch)
        assertTrue(item.issues.isEmpty())
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

    @Test
    fun `reassembles right aligned prices split into separate OCR fragments`() = runBlocking {
        val lines = listOf(
            OcrLine("SHOP", OcrBoundingBox(20, 10, 100, 35)),
            OcrLine("WHOLE MILK 2PT", OcrBoundingBox(20, 100, 230, 130)),
            OcrLine("1.70", OcrBoundingBox(500, 96, 565, 124)),
            OcrLine("SUBTOTAL", OcrBoundingBox(20, 180, 150, 210)),
            OcrLine("1.70", OcrBoundingBox(500, 176, 565, 204)),
            OcrLine("TOTAL", OcrBoundingBox(20, 240, 100, 270)),
            OcrLine("1.70", OcrBoundingBox(500, 236, 565, 264)),
        )

        val result = extractor.extract(
            OcrResult(
                text = lines.joinToString("\n") { it.text },
                lines = lines,
            ),
            emptyList(),
        )

        assertEquals("WHOLE MILK 2PT 1.70", result.lineItems.single().rawText)
        assertEquals(170, result.lineItems.single().lineTotalMinor)
        assertEquals(ReceiptTotals(170, null, 170), result.totals)
        assertTrue(result.validation.isValid)
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
