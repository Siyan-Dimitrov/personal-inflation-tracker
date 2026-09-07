package com.siyandimitrov.pocketindex.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.json.JSONArray
import org.json.JSONObject

class ClaudeReceiptCodecTest {
    @Test
    fun `request body carries the model, the photo before the prompt and a strict schema`() {
        val body = JSONObject(buildClaudeRequestBody("claude-sonnet-5", "AAAA"))

        assertEquals("claude-sonnet-5", body.getString("model"))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("AAAA", content.getJSONObject(0).getJSONObject("source").getString("data"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
        val format = body.getJSONObject("output_config").getJSONObject("format")
        assertEquals("json_schema", format.getString("type"))
        val schema = format.getJSONObject("schema")
        assertFalse(schema.getBoolean("additionalProperties"))
        val line = schema.getJSONObject("properties").getJSONObject("lines").getJSONObject("items")
        assertFalse(line.getBoolean("additionalProperties"))
        assertEquals(line.getJSONObject("properties").length(), line.getJSONArray("required").length())
    }

    @Test
    fun `the receipt is read from the text block of a finished reply`() {
        val receipt = """{"merchant":"Tesco","date":"2026-08-09","subtotal_pence":null,"tax_pence":null,
            "total_pence":150,"lines":[{"description":"MILK","quantity":1,"unit_price_pence":150,
            "line_total_pence":150,"is_discount":false}]}"""
        val envelope = claudeReply(receipt, stopReason = "end_turn")

        val parsed = assertNotNull(parseClaudeReceipt(envelope))
        assertEquals("Tesco", parsed.merchant)
        assertEquals(150, parsed.totalMinor)
        assertEquals(1, parsed.lines.size)
    }

    @Test
    fun `refusals, cut-off replies and malformed bodies yield nothing`() {
        val receipt = """{"total_pence":150,"lines":[]}"""
        assertNull(parseClaudeReceipt(claudeReply(receipt, stopReason = "refusal")))
        assertNull(parseClaudeReceipt(claudeReply(receipt, stopReason = "max_tokens")))
        assertNull(parseClaudeReceipt("not json"))
        assertNull(parseClaudeReceipt("""{"stop_reason":"end_turn","content":[]}"""))
    }

    private fun claudeReply(text: String, stopReason: String): String =
        JSONObject()
            .put("stop_reason", stopReason)
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
            .toString()
}
