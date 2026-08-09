package com.siyandimitrov.pocketindex.suggestions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class GeminiSuggestionCodecTest {
    private val descriptions = listOf("AB ROOSTER POTS 2KG", "ILIADA EVOO 1L")
    private val productNames = listOf("Potatoes", "Olive Oil", "Whole Milk")

    @Test
    fun `request body carries both lists and asks for strict JSON`() {
        val body = JSONObject(buildSuggestionRequestBody(descriptions, productNames))

        val prompt = body.getJSONArray("contents").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue("AB ROOSTER POTS 2KG" in prompt)
        assertTrue("Olive Oil" in prompt)
        assertEquals(
            "application/json",
            body.getJSONObject("generationConfig").getString("responseMimeType"),
        )
    }

    @Test
    fun `response text is read from the Gemini candidate envelope`() {
        val envelope = """
            {"candidates":[{"content":{"parts":[{"text":"{\"AB ROOSTER POTS 2KG\":\"Potatoes\"}"}],
            "role":"model"},"finishReason":"STOP"}]}
        """.trimIndent()

        assertEquals(
            """{"AB ROOSTER POTS 2KG":"Potatoes"}""",
            extractResponseText(envelope),
        )
    }

    @Test
    fun `suggestions keep only real lines and real catalogue names`() {
        val parsed = parseSuggestions(
            text = """
                {
                  "AB ROOSTER POTS 2KG": "potatoes",
                  "ILIADA EVOO 1L": "Truffle Oil",
                  "INVENTED LINE": "Whole Milk"
                }
            """.trimIndent(),
            descriptions = descriptions,
            productNames = productNames,
        )

        // Case-insensitive catalogue match is accepted; a hallucinated product
        // ("Truffle Oil") and an invented receipt line are both dropped.
        assertEquals(mapOf("AB ROOSTER POTS 2KG" to "Potatoes"), parsed)
    }

    @Test
    fun `malformed model output yields no suggestions`() {
        assertEquals(
            emptyMap(),
            parseSuggestions("not json at all", descriptions, productNames),
        )
    }

    @Test
    fun `service without a key is disabled and never suggests`() = runBlocking {
        val service = GeminiProductSuggestionService(apiKey = "")

        assertTrue(!service.isEnabled)
        assertEquals(emptyMap(), service.suggestProducts(descriptions, productNames))
    }
}
