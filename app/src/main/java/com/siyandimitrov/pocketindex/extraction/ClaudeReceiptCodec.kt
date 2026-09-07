package com.siyandimitrov.pocketindex.extraction

import org.json.JSONArray
import org.json.JSONObject

/**
 * Claude Messages API body: the photo then the prompt in one user turn, with the reply held to
 * the receipt schema by structured outputs, so the JSON always parses.
 */
fun buildClaudeRequestBody(model: String, imageBase64: String): String =
    JSONObject()
        .put("model", model)
        .put("max_tokens", CLAUDE_MAX_TOKENS)
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "image")
                                    .put(
                                        "source",
                                        JSONObject()
                                            .put("type", "base64")
                                            .put("media_type", "image/jpeg")
                                            .put("data", imageBase64),
                                    ),
                            )
                            .put(JSONObject().put("type", "text").put("text", VISION_PROMPT)),
                    ),
            ),
        )
        .put(
            "output_config",
            JSONObject().put(
                "format",
                JSONObject().put("type", "json_schema").put("schema", claudeReceiptSchema()),
            ),
        )
        .toString()

/**
 * Reads the receipt out of a Claude response: the first text block holds the JSON. A refusal
 * or a reply cut short by the token limit yields null, as does anything malformed.
 */
fun parseClaudeReceipt(responseBody: String): VisionReceipt? = runCatching {
    val response = JSONObject(responseBody)
    if (response.optString("stop_reason") != "end_turn") return null
    val blocks = response.getJSONArray("content")
    (0 until blocks.length())
        .map(blocks::getJSONObject)
        .first { it.getString("type") == "text" }
        .getString("text")
}.getOrNull()?.let(::parseVisionReceiptJson)

/** The receipt schema in the subset structured outputs accepts: `anyOf` for nullable fields. */
private fun claudeReceiptSchema(): JSONObject {
    fun type(name: String) = JSONObject().put("type", name)
    fun nullable(name: String) =
        JSONObject().put("anyOf", JSONArray().put(type(name)).put(type("null")))
    val line = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("description", type("string"))
                .put("quantity", type("number"))
                .put("unit_price_pence", nullable("integer"))
                .put("line_total_pence", type("integer"))
                .put("is_discount", type("boolean")),
        )
        .put(
            "required",
            JSONArray().put("description").put("quantity").put("unit_price_pence")
                .put("line_total_pence").put("is_discount"),
        )
        .put("additionalProperties", false)
    return JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("merchant", nullable("string"))
                .put("date", nullable("string"))
                .put("subtotal_pence", nullable("integer"))
                .put("tax_pence", nullable("integer"))
                .put("total_pence", nullable("integer"))
                .put("lines", JSONObject().put("type", "array").put("items", line)),
        )
        .put(
            "required",
            JSONArray().put("merchant").put("date").put("subtotal_pence").put("tax_pence")
                .put("total_pence").put("lines"),
        )
        .put("additionalProperties", false)
}

private const val CLAUDE_MAX_TOKENS = 4_000
