package com.siyandimitrov.pocketindex.extraction

import kotlin.math.max

object ReceiptTextNormaliser {
    private val packSize = Regex(
        """\b(?:\d+(?:[.,]\d+)?\s*[x×]\s*)?\d+(?:[.,]\d+)?\s*(?:kg|g|ml|l|ltr|litre|litres|pt|pint|pints|pk|pack)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val price = Regex("""(?:£\s*)?-?\d+[.,]\d{2}-?|\b\d+\s*p\b""", RegexOption.IGNORE_CASE)
    private val nonAlphaNumeric = Regex("""[^\p{L}\p{N}]+""")
    private val repeatedWhitespace = Regex("""\s+""")

    fun normalise(value: String): String =
        value
            .lowercase()
            .replace(packSize, " ")
            .replace(price, " ")
            .replace('&', ' ')
            .replace(nonAlphaNumeric, " ")
            .replace(repeatedWhitespace, " ")
            .trim()
}

object StringSimilarity {
    fun normalisedEditSimilarity(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isEmpty() || second.isEmpty()) return 0.0
        val longest = max(first.length, second.length)
        return 1.0 - levenshteinDistance(first, second).toDouble() / longest
    }

    fun trigramSimilarity(first: String, second: String): Double {
        if (first == second) return 1.0
        val left = trigrams(first)
        val right = trigrams(second)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size
        return (2.0 * intersection) / (left.size + right.size)
    }

    fun combined(first: String, second: String): Double {
        val normalisedFirst = ReceiptTextNormaliser.normalise(first)
        val normalisedSecond = ReceiptTextNormaliser.normalise(second)
        if (normalisedFirst.isEmpty() || normalisedSecond.isEmpty()) return 0.0
        val edit = normalisedEditSimilarity(normalisedFirst, normalisedSecond)
        val trigram = trigramSimilarity(normalisedFirst, normalisedSecond)
        return (edit * 0.55 + trigram * 0.45).coerceIn(0.0, 1.0)
    }

    private fun levenshteinDistance(first: String, second: String): Int {
        var previous = IntArray(second.length + 1) { it }
        for (firstIndex in first.indices) {
            val current = IntArray(second.length + 1)
            current[0] = firstIndex + 1
            for (secondIndex in second.indices) {
                val substitution = previous[secondIndex] +
                    if (first[firstIndex] == second[secondIndex]) 0 else 1
                current[secondIndex + 1] = minOf(
                    current[secondIndex] + 1,
                    previous[secondIndex + 1] + 1,
                    substitution,
                )
            }
            previous = current
        }
        return previous[second.length]
    }

    private fun trigrams(value: String): Set<String> {
        val padded = "  $value  "
        if (padded.length < 3) return emptySet()
        return (0..padded.length - 3).mapTo(linkedSetOf()) { padded.substring(it, it + 3) }
    }
}

class FuzzyProductMatcher(
    private val minimumConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
) {
    fun bestMatch(rawDescription: String, candidates: List<ProductCandidate>): ProductMatch? =
        rank(rawDescription, candidates, limit = 1)
            .firstOrNull()
            ?.takeIf { it.confidence >= minimumConfidence }

    fun rank(
        rawDescription: String,
        candidates: List<ProductCandidate>,
        limit: Int = 10,
    ): List<ProductMatch> {
        if (limit <= 0) return emptyList()
        return candidates
            .mapNotNull { candidate ->
                val names = listOf(candidate.canonicalName) + candidate.aliases
                val best = names
                    .map { name -> name to StringSimilarity.combined(rawDescription, name) }
                    .maxByOrNull { it.second }
                    ?: return@mapNotNull null
                ProductMatch(
                    productId = candidate.id,
                    canonicalName = candidate.canonicalName,
                    confidence = best.second,
                    matchedAgainst = best.first,
                )
            }
            .sortedByDescending(ProductMatch::confidence)
            .take(limit)
    }

    companion object {
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.62
        const val REVIEW_CONFIDENCE = 0.82
    }
}

