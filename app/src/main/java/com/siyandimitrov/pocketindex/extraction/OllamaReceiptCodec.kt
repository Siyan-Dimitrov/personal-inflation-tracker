package com.siyandimitrov.pocketindex.extraction

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * What a vision model read from a receipt photo. Amounts are pence; a discount is negative.
 */
data class VisionReceipt(
    val merchant: String?,
    /** YYYY-MM-DD as printed on the receipt, or null. */
    val purchasedAt: String?,
    val subtotalMinor: Int?,
    val taxMinor: Int?,
    val totalMinor: Int?,
    val lines: List<VisionLine>,
) {
    /**
     * The same arithmetic the review screen insists on before a receipt can be confirmed. A read
     * that fails it is not trusted at all; the on-device pipeline takes over instead.
     */
    fun reconciles(toleranceMinor: Int = 2): Boolean {
        val total = totalMinor ?: return false
        if (lines.isEmpty()) return false
        val expectedSubtotal = subtotalMinor ?: (total - (taxMinor ?: 0))
        if (abs(lines.sumOf(VisionLine::lineTotalMinor) - expectedSubtotal) > toleranceMinor) {
            return false
        }
        val subtotal = subtotalMinor
        val tax = taxMinor
        return subtotal == null || tax == null || abs(subtotal + tax - total) <= toleranceMinor
    }

    /**
     * Re-expresses the reading as receipt text in the layout the deterministic parser handles
     * best, so product matching, pack sizes, promotions and totals all go through the existing
     * rules and the review screen sees the same kind of evidence it always has.
     */
    fun toOcrResult(): OcrResult {
        val rows = buildList {
            merchant?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
            purchasedAt?.let(::toReceiptDate)?.let(::add)
            lines.forEach { line ->
                val description = line.description.trim().ifEmpty { "ITEM" }
                if (line.quantity == 1.0) {
                    add("$description ${line.lineTotalMinor.asPounds()}")
                } else {
                    val unit = line.unitPriceMinor
                        ?: (line.lineTotalMinor / line.quantity).roundToInt()
                    add(
                        "$description ${line.quantity.asQuantity()} @ ${unit.asPounds()} " +
                            line.lineTotalMinor.asPounds(),
                    )
                }
            }
            subtotalMinor?.let { add("SUBTOTAL ${it.asPounds()}") }
            taxMinor?.let { add("VAT ${it.asPounds()}") }
            totalMinor?.let { add("TOTAL ${it.asPounds()}") }
        }
        return OcrResult(text = rows.joinToString("\n"), lines = rows.map { OcrLine(it) })
    }
}

data class VisionLine(
    val description: String,
    val quantity: Double,
    val unitPriceMinor: Int?,
    val lineTotalMinor: Int,
)

/** Ollama `/api/chat` body: one user turn with the photo, constrained to the receipt schema. */
fun buildVisionRequestBody(model: String, imageBase64: String): String =
    JSONObject()
        .put("model", model)
        .put("stream", false)
        .put("think", false)
        .put("format", receiptSchema())
        .put("options", JSONObject().put("temperature", 0).put("num_predict", 4_000))
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", VISION_PROMPT)
                    .put("images", JSONArray().put(imageBase64)),
            ),
        )
        .toString()

/**
 * Reads the receipt out of an Ollama chat response. Anything malformed yields null rather than
 * a partial receipt: a half-read receipt would only fail reconciliation later anyway.
 */
fun parseVisionReceipt(responseBody: String): VisionReceipt? = runCatching {
    val content = JSONObject(responseBody).getJSONObject("message").getString("content")
    val json = JSONObject(content)
    val lines = json.getJSONArray("lines")
    VisionReceipt(
        merchant = json.optNullableString("merchant"),
        purchasedAt = json.optNullableString("date")?.takeIf(isoDateRegex::matches),
        subtotalMinor = json.optNullableInt("subtotal_pence"),
        taxMinor = json.optNullableInt("tax_pence"),
        totalMinor = json.optNullableInt("total_pence"),
        lines = List(lines.length()) { index ->
            val line = lines.getJSONObject(index)
            // The explicit flag decides the sign: models read "2.00-" as a plain amount far more
            // often than they forget to label a rebate line as a discount.
            val discount = line.optBoolean("is_discount", false)
            val total = line.getInt("line_total_pence")
            val unit = line.optNullableInt("unit_price_pence")
            VisionLine(
                description = line.getString("description"),
                quantity = line.optDouble("quantity", 1.0).takeIf { it > 0.0 } ?: 1.0,
                unitPriceMinor = unit?.let { if (discount) -abs(it) else it },
                lineTotalMinor = if (discount) -abs(total) else total,
            )
        },
    )
}.getOrNull()

private const val VISION_PROMPT =
    "This is a photo of a UK shop receipt. Read it and answer with JSON only.\n" +
        "merchant: the shop's name. date: the transaction date printed on the receipt as " +
        "YYYY-MM-DD (UK receipts print day first), or null.\n" +
        "subtotal_pence, tax_pence, total_pence: the printed subtotal, VAT and grand total as " +
        "integer pence, or null when not printed.\n" +
        "lines: every purchased item and every discount, coupon, saving, rebate or refund " +
        "line, in order, with description copied exactly as printed, quantity, " +
        "unit_price_pence and line_total_pence as integer pence, and is_discount. Set " +
        "is_discount true when the line takes money off: a minus sign before or after its " +
        "amount (for example \"2.00-\" or \"-2.00\"), or a coupon, saving, IRC or rebate " +
        "label. Every other line has is_discount false. Do not include subtotal, VAT, total, " +
        "payment, change, loyalty points or VAT breakdown rows as lines. Never invent lines " +
        "or amounts."

private fun receiptSchema(): JSONObject {
    fun nullable(type: String) = JSONObject().put("type", JSONArray().put(type).put("null"))
    val line = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("description", JSONObject().put("type", "string"))
                .put("quantity", JSONObject().put("type", "number"))
                .put("unit_price_pence", nullable("integer"))
                .put("line_total_pence", JSONObject().put("type", "integer"))
                .put("is_discount", JSONObject().put("type", "boolean")),
        )
        .put(
            "required",
            JSONArray().put("description").put("quantity").put("line_total_pence").put("is_discount"),
        )
    // Every field is required: a constrained model emits only what the schema demands, and a
    // nullable field left out is a subtotal or VAT the reconciliation never sees.
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
}

private val isoDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

private fun JSONObject.optNullableInt(key: String): Int? =
    if (isNull(key)) null else runCatching { getInt(key) }.getOrNull()

/** "2026-07-16" -> "16/07/2026", the day-first form the parser reads. */
private fun toReceiptDate(iso: String): String {
    val (year, month, day) = iso.split("-")
    return "$day/$month/$year"
}

private fun Int.asPounds(): String =
    (if (this < 0) "-" else "") + "%d.%02d".format(Locale.ROOT, abs(this) / 100, abs(this) % 100)

private fun Double.asQuantity(): String =
    if (this == Math.rint(this)) toInt().toString() else "%.3f".format(Locale.ROOT, this).trimEnd('0')
