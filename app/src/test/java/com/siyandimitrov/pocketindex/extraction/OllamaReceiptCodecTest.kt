package com.siyandimitrov.pocketindex.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class OllamaReceiptCodecTest {
    /** The Costco receipt in the repo root: ex-VAT lines, three "IRC" rebates, VAT on top. */
    private val costco = VisionReceipt(
        merchant = "Costco Wholesale",
        purchasedAt = "2026-07-16",
        subtotalMinor = 14315,
        taxMinor = 349,
        totalMinor = 14664,
        lines = listOf(
            VisionLine("WATERMELON EACH", 1.0, 549, 549),
            VisionLine("KS AA BURGERS", 1.0, 1999, 1999),
            VisionLine("KS SEA SALT 850G", 1.0, 239, 239),
            VisionLine("AA QUICK FRY STEAK", 1.0, 2608, 2608),
            VisionLine("PORK BURGERS X10", 1.0, 999, 999),
            VisionLine("CHICKEN FILLETS", 1.0, 1391, 1391),
            VisionLine("CHICKEN FILLETS", 1.0, 1372, 1372),
            VisionLine("CHICKEN FILLETS", 1.0, 1344, 1344),
            VisionLine("GILLETTE GEL 6X200", 1.0, 949, 949),
            VisionLine("GILLETTE FUSION 5L", 1.0, -200, -200),
            VisionLine("FUJI APPLE 2KG", 1.0, 499, 499),
            VisionLine("HASS AVOCADOS", 1.0, 499, 499),
            VisionLine("IRC HASS AVOCADOS", 1.0, -100, -100),
            VisionLine("ILIADA EVOO 1L", 1.0, 799, 799),
            VisionLine("LIMES 16 PACK", 1.0, 369, 369),
            VisionLine("SKECHERS SOCK 8PK", 1.0, 1299, 1299),
            VisionLine("IRC SKECHERS CREW", 1.0, -300, -300),
        ),
    )

    @Test
    fun `request body carries the model, the image and a JSON schema`() {
        val body = JSONObject(buildVisionRequestBody("qwen3.5:cloud", "AAAA"))

        assertEquals("qwen3.5:cloud", body.getString("model"))
        assertFalse(body.getBoolean("stream"))
        val message = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("AAAA", message.getJSONArray("images").getString(0))
        assertTrue("is_discount" in message.getString("content"))
        val schema = body.getJSONObject("format")
        assertEquals("object", schema.getString("type"))
        val required = schema.getJSONArray("required").let { array -> List(array.length(), array::getString) }
        assertTrue("subtotal_pence" in required && "tax_pence" in required && "date" in required)
    }

    @Test
    fun `response content is parsed into a receipt`() {
        val content = """
            {"merchant":"Tesco","date":"2026-08-09","subtotal_pence":null,"tax_pence":null,
             "total_pence":400,"lines":[
               {"description":"MILK 2 PINTS","quantity":1,"unit_price_pence":150,"line_total_pence":150,"is_discount":false},
               {"description":"BREAD","quantity":2,"unit_price_pence":150,"line_total_pence":300,"is_discount":false},
               {"description":"CLUBCARD PRICE","quantity":1,"unit_price_pence":50,"line_total_pence":50,"is_discount":true}]}
        """.trimIndent()
        val envelope = JSONObject()
            .put("message", JSONObject().put("role", "assistant").put("content", content))
            .toString()

        val receipt = assertNotNull(parseVisionReceipt(envelope))

        assertEquals("Tesco", receipt.merchant)
        assertEquals("2026-08-09", receipt.purchasedAt)
        assertNull(receipt.subtotalMinor)
        assertEquals(400, receipt.totalMinor)
        assertEquals(2.0, receipt.lines[1].quantity)
        // The flag, not the sign the model happened to write, makes a discount negative.
        assertEquals(-50, receipt.lines[2].lineTotalMinor)
        assertEquals(-50, receipt.lines[2].unitPriceMinor)
        assertTrue(receipt.reconciles())
    }

    @Test
    fun `malformed responses and a date in the wrong shape are rejected`() {
        assertNull(parseVisionReceipt("not json"))
        assertNull(parseVisionReceipt("""{"message":{"content":"{\"total_pence\":1}"}}"""))
        val envelope = JSONObject().put(
            "message",
            JSONObject().put("content", """{"date":"16/07/2026","total_pence":100,"lines":[]}"""),
        ).toString()
        assertNull(assertNotNull(parseVisionReceipt(envelope)).purchasedAt)
    }

    @Test
    fun `a reading only reconciles when signed lines meet the printed totals`() {
        assertTrue(costco.reconciles())
        // The 9B model's actual mistake: rebates read as positive amounts.
        val rebatesPositive = costco.copy(
            lines = costco.lines.map { it.copy(lineTotalMinor = kotlin.math.abs(it.lineTotalMinor)) },
        )
        assertFalse(rebatesPositive.reconciles())
        assertFalse(costco.copy(totalMinor = null).reconciles())
        assertFalse(costco.copy(lines = emptyList()).reconciles())
        assertFalse(costco.copy(taxMinor = 400).reconciles())
    }

    @Test
    fun `the canonical text goes through the rules parser intact`() = runBlocking {
        val result = RuleBasedReceiptExtractor().extract(costco.toOcrResult(), emptyList())

        assertEquals("Costco Wholesale", result.merchantName)
        assertEquals("2026-07-16", result.purchasedAt)
        assertEquals(ReceiptTotals(subtotalMinor = 14315, taxMinor = 349, totalMinor = 14664), result.totals)
        assertTrue(result.validation.isValid, result.validation.issues.toString())
        assertEquals(costco.lines.map { it.lineTotalMinor }, result.lineItems.map { it.lineTotalMinor })
        assertEquals("GILLETTE FUSION 5L", result.lineItems[9].description)
        assertEquals(5_000.0, assertNotNull(result.lineItems[9].packSize).amount)
    }

    @Test
    fun `quantities and unit prices survive the round trip`() = runBlocking {
        val receipt = VisionReceipt(
            merchant = "Lidl",
            purchasedAt = null,
            subtotalMinor = null,
            taxMinor = null,
            totalMinor = 1_050,
            lines = listOf(
                VisionLine("OAT DRINK 1L", 3.0, 250, 750),
                VisionLine("BANANAS", 1.0, 300, 300),
            ),
        )

        val result = RuleBasedReceiptExtractor().extract(receipt.toOcrResult(), emptyList())

        // No subtotal is printed, so the only validation note is the one the review derives past.
        assertEquals(setOf(ValidationIssue.SUBTOTAL_MISSING), result.validation.issues)
        assertEquals(1_050, result.totals.totalMinor)
        val oats = result.lineItems.first()
        assertEquals("OAT DRINK 1L", oats.description)
        assertEquals(3.0, oats.quantity)
        assertEquals(250, oats.unitPriceMinor)
        assertEquals(750, oats.lineTotalMinor)
        assertTrue(oats.issues.isEmpty() || oats.issues == setOf(LineItemIssue.NO_PRODUCT_MATCH))
    }
}
