package com.siyandimitrov.pocketindex.suggestions

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Product suggestions from the Gemini API free tier.
 *
 * The key is baked in at build time from local.properties and never committed; without one the
 * service reports disabled and no request is ever made. Only unmatched line descriptions and the
 * user's catalogue names are sent — never prices, totals, dates, or images.
 */
class GeminiProductSuggestionService(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : ProductSuggestionService {

    override val isEnabled: Boolean
        get() = apiKey.isNotBlank()

    override suspend fun suggestProducts(
        descriptions: List<String>,
        productNames: List<String>,
    ): Map<String, String> {
        if (!isEnabled || descriptions.isEmpty() || productNames.isEmpty()) return emptyMap()
        return runCatching {
            val body = buildSuggestionRequestBody(descriptions, productNames)
            val response = withContext(Dispatchers.IO) { post(body) }
            parseSuggestions(extractResponseText(response), descriptions, productNames)
        }.getOrDefault(emptyMap())
    }

    private fun post(body: String): String {
        val connection = URL("$BASE_URL/$model:generateContent")
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            // The key travels in a header so it can never appear in a logged URL.
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Gemini returned HTTP ${connection.responseCode}."
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        /** Flash-Lite carries the most generous free-tier daily quota. */
        const val DEFAULT_MODEL = "gemini-2.5-flash-lite"
        const val TIMEOUT_MILLIS = 15_000
    }
}

internal fun buildSuggestionRequestBody(
    descriptions: List<String>,
    productNames: List<String>,
): String {
    val prompt = buildString {
        appendLine("You match supermarket receipt lines to a personal product catalogue.")
        appendLine("Receipt lines are abbreviated UK till text; catalogue names are canonical products.")
        appendLine("Reply with a JSON object mapping each receipt line, quoted verbatim, to exactly one catalogue name.")
        appendLine("Only use names from the catalogue. Omit a line when no catalogue entry is the same kind of item.")
        appendLine("A different brand or size of the same product is a match; a different product that merely shares a word is not.")
        appendLine()
        appendLine("Catalogue: ${JSONArray(productNames)}")
        append("Receipt lines: ${JSONArray(descriptions)}")
    }
    return JSONObject()
        .put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt)),
                ),
            ),
        )
        .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        .toString()
}

internal fun extractResponseText(responseBody: String): String =
    JSONObject(responseBody)
        .getJSONArray("candidates").getJSONObject(0)
        .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
        .getString("text")

/**
 * Keeps only mappings whose line and product both actually exist, so a hallucinated product
 * name or an invented receipt line can never produce a suggestion.
 */
internal fun parseSuggestions(
    text: String,
    descriptions: List<String>,
    productNames: List<String>,
): Map<String, String> {
    val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
    val namesByLowercase = productNames.associateBy(String::lowercase)
    val validLines = descriptions.toSet()
    return buildMap {
        json.keys().forEach { line ->
            val canonical = namesByLowercase[json.optString(line).lowercase()]
            if (line in validLines && canonical != null) put(line, canonical)
        }
    }
}
