package com.siyandimitrov.pocketindex.suggestions

/**
 * Suggests catalogue products for receipt lines the deterministic matcher could not resolve.
 *
 * An implementation may call a remote model, so a suggestion is only ever a proposal for the
 * review screen — it is stored at below-confident match confidence and never reaches the index
 * without the user confirming the receipt.
 */
interface ProductSuggestionService {
    /** False when no credentials were provided at build time; the app then stays fully offline. */
    val isEnabled: Boolean

    /**
     * Maps receipt line descriptions to canonical product names drawn from [productNames],
     * omitting lines with no plausible match. Failures of any kind return an empty map so a
     * network problem can never break receipt extraction.
     */
    suspend fun suggestProducts(
        descriptions: List<String>,
        productNames: List<String>,
    ): Map<String, String>
}
