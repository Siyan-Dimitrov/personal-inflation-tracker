package com.siyandimitrov.pocketindex.extraction

/**
 * Android-free representation of text returned by an OCR engine.
 *
 * [text] and [OcrLine.text] are evidence and must be stored without correction. Any cleanup used
 * by the parser is derived from these values.
 */
data class OcrResult(
    val text: String,
    val lines: List<OcrLine>,
    /** How the text was produced, in words for the receipt screens; null when unknown. */
    val readBy: String? = null,
)

data class OcrLine(
    val text: String,
    val boundingBox: OcrBoundingBox? = null,
)

data class OcrBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

enum class BaseUnit {
    MASS_G,
    VOLUME_ML,
    COUNT,
    UNKNOWN,
}

/**
 * A pack size converted to the base unit used by price observations.
 *
 * Examples: 2PT -> 1136 ml, 6 x 330ML -> 1980 ml, 6PK -> 6 count.
 */
data class ParsedPackSize(
    val amount: Double,
    val baseUnit: BaseUnit,
    val sourceText: String,
    val componentCount: Double? = null,
    val componentSize: Double? = null,
)

/**
 * Lightweight canonical product projection supplied by the data layer.
 *
 * Keeping this projection in extraction prevents the parser from depending on Room entities.
 */
data class ProductCandidate(
    val id: Long,
    val canonicalName: String,
    val aliases: List<String> = emptyList(),
    val packSize: Double? = null,
    val baseUnit: BaseUnit = BaseUnit.UNKNOWN,
)

data class ProductMatch(
    val productId: Long,
    val canonicalName: String,
    val confidence: Double,
    val matchedAgainst: String,
)

enum class LineItemIssue {
    MISSING_PACK_SIZE,
    AMBIGUOUS_PRICE,
    NO_PRODUCT_MATCH,
    LOW_CONFIDENCE_MATCH,
}

data class ExtractedLineItem(
    /** Exact OCR line; never normalised or corrected. */
    val rawText: String,
    val description: String,
    val quantity: Double,
    val unitPriceMinor: Int?,
    val lineTotalMinor: Int,
    val packSize: ParsedPackSize?,
    val productMatch: ProductMatch? = null,
    val issues: Set<LineItemIssue> = emptySet(),
)

data class ReceiptTotals(
    val subtotalMinor: Int? = null,
    val taxMinor: Int? = null,
    val totalMinor: Int? = null,
)

enum class ValidationIssue {
    NO_LINE_ITEMS,
    SUBTOTAL_MISSING,
    TOTAL_MISSING,
    LINE_ITEMS_DO_NOT_EQUAL_SUBTOTAL,
    SUBTOTAL_PLUS_TAX_DOES_NOT_EQUAL_TOTAL,
    SUBTOTAL_DOES_NOT_EQUAL_TOTAL,
}

data class ReceiptValidation(
    val isValid: Boolean,
    val issues: Set<ValidationIssue>,
    val calculatedLineItemsMinor: Int,
    val lineItemsDifferenceMinor: Int? = null,
    val totalDifferenceMinor: Int? = null,
    val toleranceMinor: Int = 2,
)

data class ExtractionResult(
    val rawOcrText: String,
    val merchantName: String?,
    /** Transaction date printed on the receipt as YYYY-MM-DD, or null when none was readable. */
    val purchasedAt: String? = null,
    val lineItems: List<ExtractedLineItem>,
    val totals: ReceiptTotals,
    val validation: ReceiptValidation,
) {
    val needsReview: Boolean
        get() = !validation.isValid || lineItems.any { it.issues.isNotEmpty() }
}
