package com.siyandimitrov.pocketindex.extraction

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Conservative deterministic parser for UK receipts.
 *
 * It only accepts a line item when a monetary amount is the final column. Lines that look like
 * payment details, dates, headings or receipt totals are ignored rather than guessed.
 */
class RuleBasedReceiptExtractor(
    private val matcher: FuzzyProductMatcher = FuzzyProductMatcher(),
    private val validationToleranceMinor: Int = 2,
) : ReceiptExtractor {

    override suspend fun extract(
        ocrResult: OcrResult,
        candidates: List<ProductCandidate>,
    ): ExtractionResult {
        val sourceLines = mergeSameRowFragments(ocrResult.lines).ifEmpty {
            ocrResult.text.lines().filter(String::isNotBlank).map { OcrLine(it) }
        }
        val classifiedTotals = sourceLines.mapIndexedNotNull { index, line ->
            classifyTotal(line.text)?.let { index to it }
        }
        val totalLineIndexes = classifiedTotals.mapTo(hashSetOf()) { it.first }
        val totals = buildTotals(classifiedTotals.map { it.second })
        val items = sourceLines.mapIndexedNotNull { index, line ->
            if (index in totalLineIndexes) {
                null
            } else {
                parseLineItem(line.text, candidates)
            }
        }
        val validation = validate(items, totals)

        return ExtractionResult(
            rawOcrText = ocrResult.text,
            merchantName = detectMerchant(sourceLines),
            lineItems = items,
            totals = totals,
            validation = validation,
        )
    }

    /**
     * ML Kit can return a receipt's description and right-aligned price as separate text lines
     * even when they share the same visual row. Reassemble those fragments from their geometry
     * before applying the deliberately conservative price-at-end parser.
     */
    private fun mergeSameRowFragments(lines: List<OcrLine>): List<OcrLine> {
        val positioned = lines.filter { it.text.isNotBlank() && it.boundingBox != null }
        val unpositioned = lines.filter { it.text.isNotBlank() && it.boundingBox == null }
        if (positioned.isEmpty()) return unpositioned

        val rows = mutableListOf<MutableList<OcrLine>>()
        positioned
            .sortedWith(
                compareBy<OcrLine> { it.boundingBox?.top ?: Int.MAX_VALUE }
                    .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE },
            )
            .forEach { line ->
                val row = rows.firstOrNull { existing ->
                    existing.any { sameVisualRow(it, line) }
                }
                if (row == null) rows += mutableListOf(line) else row += line
            }

        val merged = rows.map { row ->
            val fragments = row.sortedBy { it.boundingBox?.left ?: Int.MAX_VALUE }
            val boxes = fragments.mapNotNull(OcrLine::boundingBox)
            OcrLine(
                text = fragments.joinToString(" ") { it.text.trim() },
                boundingBox = OcrBoundingBox(
                    left = boxes.minOf(OcrBoundingBox::left),
                    top = boxes.minOf(OcrBoundingBox::top),
                    right = boxes.maxOf(OcrBoundingBox::right),
                    bottom = boxes.maxOf(OcrBoundingBox::bottom),
                ),
            )
        }
        return (merged + unpositioned).sortedWith(
            compareBy<OcrLine> { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE },
        )
    }

    private fun sameVisualRow(first: OcrLine, second: OcrLine): Boolean {
        val firstBox = first.boundingBox ?: return false
        val secondBox = second.boundingBox ?: return false
        val overlap = minOf(firstBox.bottom, secondBox.bottom) -
            maxOf(firstBox.top, secondBox.top)
        val smallerHeight = minOf(
            firstBox.bottom - firstBox.top,
            secondBox.bottom - secondBox.top,
        ).coerceAtLeast(1)
        return overlap > 0 && overlap.toDouble() / smallerHeight >= MINIMUM_ROW_OVERLAP
    }

    fun parsePackSize(text: String): ParsedPackSize? {
        multiPackRegex.find(text)?.let { match ->
            val count = match.groupValues[1].decimal()
            val component = match.groupValues[2].decimal()
            val (baseComponent, baseUnit) = toBaseUnit(component, match.groupValues[3])
            return ParsedPackSize(
                amount = count * baseComponent,
                baseUnit = baseUnit,
                sourceText = match.value,
                componentCount = count,
                componentSize = baseComponent,
            )
        }

        countPackRegex.find(text)?.let { match ->
            val count = match.groupValues[1].decimal()
            return ParsedPackSize(
                amount = count,
                baseUnit = BaseUnit.COUNT,
                sourceText = match.value,
                componentCount = count,
                componentSize = 1.0,
            )
        }

        singlePackRegex.find(text)?.let { match ->
            val amount = match.groupValues[1].decimal()
            val (baseAmount, baseUnit) = toBaseUnit(amount, match.groupValues[2])
            return ParsedPackSize(
                amount = baseAmount,
                baseUnit = baseUnit,
                sourceText = match.value,
            )
        }
        return null
    }

    private fun parseLineItem(
        rawText: String,
        candidates: List<ProductCandidate>,
    ): ExtractedLineItem? {
        if (shouldIgnore(rawText)) return null
        val amountMatches = moneyRegex.findAll(rawText).toList()
        val finalAmount = amountMatches.lastOrNull()?.takeIf { match ->
            rawText.substring(match.range.last + 1).isBlank()
        } ?: return null

        val lineTotalMinor = finalAmount.toMinor() ?: return null
        var description = rawText.substring(0, finalAmount.range.first).trim()
        val quantity = parsePurchaseQuantity(description)
        description = stripPurchaseQuantity(description)

        val earlierAmounts = amountMatches.dropLast(1)
            .filter { it.range.first < finalAmount.range.first }
        val explicitUnitPrice = earlierAmounts.lastOrNull()?.toMinor()
        val calculatedUnitPrice = if (quantity > 0.0) {
            (lineTotalMinor / quantity).roundToInt()
        } else {
            null
        }
        val unitPrice = explicitUnitPrice ?: calculatedUnitPrice

        // Price columns before the total are not part of the product description.
        earlierAmounts.firstOrNull()?.let { description = rawText.substring(0, it.range.first).trim() }
        description = stripPurchaseQuantity(description).trim(' ', '-', ':')
        if (description.isBlank() || description.none(Char::isLetter)) return null

        val rankedMatch = matcher.rank(description, candidates, limit = 1).firstOrNull()
        val acceptedMatch = rankedMatch?.takeIf { it.confidence >= MINIMUM_SUGGESTION_CONFIDENCE }
        val matchedCandidate = acceptedMatch?.let { match ->
            candidates.firstOrNull { it.id == match.productId }
        }
        val parsedPack = parsePackSize(description)
        val inheritedPack = if (parsedPack == null &&
            matchedCandidate?.packSize != null &&
            matchedCandidate.baseUnit != BaseUnit.UNKNOWN
        ) {
            ParsedPackSize(
                amount = matchedCandidate.packSize,
                baseUnit = matchedCandidate.baseUnit,
                sourceText = "candidate default",
            )
        } else {
            null
        }

        val issues = buildSet {
            if (earlierAmounts.size > 1 ||
                (explicitUnitPrice != null &&
                    abs(explicitUnitPrice * quantity - lineTotalMinor) > validationToleranceMinor)
            ) {
                add(LineItemIssue.AMBIGUOUS_PRICE)
            }
            if (acceptedMatch == null) {
                add(LineItemIssue.NO_PRODUCT_MATCH)
            } else if (acceptedMatch.confidence < FuzzyProductMatcher.REVIEW_CONFIDENCE) {
                add(LineItemIssue.LOW_CONFIDENCE_MATCH)
            }
            if (parsedPack == null && inheritedPack != null) {
                add(LineItemIssue.MISSING_PACK_SIZE)
            }
        }

        return ExtractedLineItem(
            rawText = rawText,
            description = description,
            quantity = quantity,
            unitPriceMinor = unitPrice,
            lineTotalMinor = lineTotalMinor,
            packSize = parsedPack ?: inheritedPack,
            productMatch = acceptedMatch,
            issues = issues,
        )
    }

    private fun validate(
        items: List<ExtractedLineItem>,
        totals: ReceiptTotals,
    ): ReceiptValidation {
        val calculated = items.sumOf(ExtractedLineItem::lineTotalMinor)
        val lineDifference = totals.subtotalMinor?.let { calculated - it }
        val totalDifference = when {
            totals.subtotalMinor == null || totals.totalMinor == null -> null
            totals.taxMinor != null -> totals.subtotalMinor + totals.taxMinor - totals.totalMinor
            else -> totals.subtotalMinor - totals.totalMinor
        }
        val issues = buildSet {
            if (items.isEmpty()) add(ValidationIssue.NO_LINE_ITEMS)
            if (totals.subtotalMinor == null) add(ValidationIssue.SUBTOTAL_MISSING)
            if (totals.totalMinor == null) add(ValidationIssue.TOTAL_MISSING)
            if (lineDifference != null && abs(lineDifference) > validationToleranceMinor) {
                add(ValidationIssue.LINE_ITEMS_DO_NOT_EQUAL_SUBTOTAL)
            }
            if (totalDifference != null && abs(totalDifference) > validationToleranceMinor) {
                add(
                    if (totals.taxMinor != null) {
                        ValidationIssue.SUBTOTAL_PLUS_TAX_DOES_NOT_EQUAL_TOTAL
                    } else {
                        ValidationIssue.SUBTOTAL_DOES_NOT_EQUAL_TOTAL
                    },
                )
            }
        }
        return ReceiptValidation(
            isValid = issues.isEmpty(),
            issues = issues,
            calculatedLineItemsMinor = calculated,
            lineItemsDifferenceMinor = lineDifference,
            totalDifferenceMinor = totalDifference,
            toleranceMinor = validationToleranceMinor,
        )
    }

    private fun buildTotals(classified: List<ClassifiedTotal>): ReceiptTotals {
        val subtotal = classified.firstOrNull { it.type == TotalType.SUBTOTAL }?.minor
        val taxes = classified.filter { it.type == TotalType.TAX }
        val tax = taxes.takeIf { it.isNotEmpty() }?.sumOf(ClassifiedTotal::minor)
        val total = classified.lastOrNull { it.type == TotalType.TOTAL }?.minor
        return ReceiptTotals(subtotalMinor = subtotal, taxMinor = tax, totalMinor = total)
    }

    private fun classifyTotal(text: String): ClassifiedTotal? {
        val trimmed = text.trim()
        val amount = moneyRegex.findAll(trimmed).lastOrNull()
            ?.takeIf { trimmed.substring(it.range.last + 1).isBlank() }
            ?.toMinor()
            ?: return null
        val label = trimmed.substringBeforeLastMoney().uppercase()
        return when {
            subtotalLabel.containsMatchIn(label) ->
                ClassifiedTotal(TotalType.SUBTOTAL, amount)
            taxLabel.containsMatchIn(label) ->
                ClassifiedTotal(TotalType.TAX, amount)
            totalLabel.containsMatchIn(label) && !nonTotalLabel.containsMatchIn(label) ->
                ClassifiedTotal(TotalType.TOTAL, amount)
            else -> null
        }
    }

    private fun detectMerchant(lines: List<OcrLine>): String? =
        lines.asSequence()
            .map(OcrLine::text)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .firstOrNull { line ->
                line.any(Char::isLetter) &&
                    moneyRegex.find(line) == null &&
                    !dateOrTimeRegex.containsMatchIn(line) &&
                    !headingRegex.matches(line)
            }

    private fun parsePurchaseQuantity(description: String): Double {
        leadingQuantityRegex.find(description)?.let { match ->
            val following = description.substring(match.range.last + 1).trimStart()
            if (!startsWithMeasuredPack.matchesAt(following, 0)) {
                return match.groupValues[1].decimal()
            }
        }
        atQuantityRegex.find(description)?.let { return it.groupValues[1].decimal() }
        qtyLabelRegex.find(description)?.let { return it.groupValues[1].decimal() }
        return 1.0
    }

    private fun stripPurchaseQuantity(description: String): String {
        leadingQuantityRegex.find(description)?.let { match ->
            val following = description.substring(match.range.last + 1).trimStart()
            if (!startsWithMeasuredPack.matchesAt(following, 0)) {
                return description.removeRange(match.range).trimStart()
            }
        }
        return description
            .replace(atQuantityRegex, " ")
            .replace(qtyLabelRegex, " ")
            .replace(danglingAtQuantityRegex, " ")
            .replace(repeatedWhitespaceRegex, " ")
            .trim()
    }

    private fun shouldIgnore(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isEmpty() ||
            headingRegex.matches(trimmed) ||
            ignoredLineRegex.containsMatchIn(trimmed) ||
            dateOrTimeRegex.containsMatchIn(trimmed)
    }

    private fun MatchResult.toMinor(): Int? {
        val numeric = groupValues[1].replace("£", "").replace(" ", "")
        val suffixMinus = groupValues[2] == "-"
        val amount = if (numeric.endsWith("p", ignoreCase = true)) {
            numeric.dropLast(1).toIntOrNull()
        } else {
            val parts = numeric.replace(',', '.').split('.')
            if (parts.size != 2 || parts[1].length != 2) return null
            val pounds = parts[0].removePrefix("-").toIntOrNull() ?: return null
            val pennies = parts[1].toIntOrNull() ?: return null
            pounds * 100 + pennies
        } ?: return null
        val negative = numeric.startsWith("-") || suffixMinus
        return if (negative) -abs(amount) else amount
    }

    private fun String.substringBeforeLastMoney(): String {
        val match = moneyRegex.findAll(this).lastOrNull() ?: return this
        return substring(0, match.range.first).trim()
    }

    private fun String.decimal(): Double = replace(',', '.').toDouble()

    private fun toBaseUnit(value: Double, printedUnit: String): Pair<Double, BaseUnit> =
        when (printedUnit.lowercase()) {
            "kg" -> value * 1_000.0 to BaseUnit.MASS_G
            "g" -> value to BaseUnit.MASS_G
            "l", "ltr", "litre", "litres" -> value * 1_000.0 to BaseUnit.VOLUME_ML
            "ml" -> value to BaseUnit.VOLUME_ML
            "pt", "pint", "pints" -> value * MILLILITRES_PER_UK_PINT to BaseUnit.VOLUME_ML
            else -> value to BaseUnit.UNKNOWN
        }

    private enum class TotalType { SUBTOTAL, TAX, TOTAL }

    private data class ClassifiedTotal(val type: TotalType, val minor: Int)

    companion object {
        private const val MILLILITRES_PER_UK_PINT = 568.0
        private const val MINIMUM_SUGGESTION_CONFIDENCE = 0.40
        private const val MINIMUM_ROW_OVERLAP = 0.35

        /**
         * Group 1 is the entire numeric price (including optional £/minus), group 2 is a trailing
         * receipt-style minus. Requiring two decimal places avoids treating weights and dates as
         * money.
         */
        private val moneyRegex =
            Regex("""(?<![\d.])((?:£\s*)?-?\d{1,5}[.,]\d{2}|\d{1,5}\s*[pP]\b)(-)?(?!\d)""")
        private val subtotalLabel =
            Regex("""\b(?:SUB\s*TOTAL|SUBTOTAL|NET\s*TOTAL)\b""", RegexOption.IGNORE_CASE)
        private val taxLabel =
            Regex("""\b(?:VAT|TAX)\b""", RegexOption.IGNORE_CASE)
        private val totalLabel =
            Regex("""\b(?:GRAND\s+TOTAL|TOTAL|AMOUNT\s+DUE)\b""", RegexOption.IGNORE_CASE)
        private val nonTotalLabel =
            Regex("""\b(?:SAVINGS?|DISCOUNT|ITEMS?)\b""", RegexOption.IGNORE_CASE)
        private val headingRegex =
            Regex("""^\s*(?:ITEM|DESCRIPTION)?\s*(?:QTY|QUANTITY)?\s*(?:PRICE|AMOUNT|TOTAL)?\s*$""", RegexOption.IGNORE_CASE)
        private val ignoredLineRegex =
            Regex(
                """\b(?:CASH|CARD|VISA|MASTERCARD|MAESTRO|AMEX|TENDER|CHANGE|BALANCE|AUTH|AID|CONTACTLESS|PAYMENT|RECEIPT\s*(?:NO|NUMBER)|TEL|TELEPHONE)\b""",
                RegexOption.IGNORE_CASE,
            )
        private val dateOrTimeRegex = Regex(
            """(?:\b\d{1,2}[/:.-]\d{1,2}[/:.-]\d{2,4}\b|\b\d{1,2}:\d{2}(?::\d{2})?\b)""",
        )
        private val leadingQuantityRegex =
            Regex("""^\s*(\d+(?:[.,]\d+)?)\s*[xX×]\s*""")
        private val atQuantityRegex =
            Regex("""\b(\d+(?:[.,]\d+)?)\s*@\s*(?:£\s*)?\d+[.,]\d{2}\b""", RegexOption.IGNORE_CASE)
        private val qtyLabelRegex =
            Regex("""\bQTY\s*[:=]?\s*(\d+(?:[.,]\d+)?)\b""", RegexOption.IGNORE_CASE)
        private val danglingAtQuantityRegex =
            Regex("""\b\d+(?:[.,]\d+)?\s*@\s*$""", RegexOption.IGNORE_CASE)
        private val startsWithMeasuredPack =
            Regex("""^\d+(?:[.,]\d+)?\s*(?:kg|g|ml|l|ltr|litre|litres|pt|pint|pints)\b""", RegexOption.IGNORE_CASE)
        private val multiPackRegex =
            Regex(
                """\b(\d+(?:[.,]\d+)?)\s*[xX×]\s*(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l|ltr|litre|litres|pt|pint|pints)\b""",
                RegexOption.IGNORE_CASE,
            )
        private val countPackRegex =
            Regex("""\b(\d+(?:[.,]\d+)?)\s*(?:pk|pack|count|ct)\b""", RegexOption.IGNORE_CASE)
        private val singlePackRegex =
            Regex(
                """\b(\d+(?:[.,]\d+)?)\s*(kg|g|ml|l|ltr|litre|litres|pt|pint|pints)\b""",
                RegexOption.IGNORE_CASE,
            )
        private val repeatedWhitespaceRegex = Regex("""\s+""")
    }
}

/**
 * Re-runs only the deterministic pack-size rule against preserved receipt text.
 *
 * The current persistence schema stores raw evidence and the confirmed pack size in observations,
 * but not the parser's draft pack size on a line item. The review layer uses this pure helper to
 * reconstruct that draft without repeating OCR or mutating the evidence.
 */
fun parseReceiptPackSize(text: String): ParsedPackSize? =
    PackSizeParserHolder.parser.parsePackSize(text)

private object PackSizeParserHolder {
    val parser = RuleBasedReceiptExtractor()
}
