package com.siyandimitrov.pocketindex.extraction

/**
 * Structures OCR output. Image decoding and OCR are deliberately separate so extraction can be
 * replayed and tested without access to the original bitmap.
 */
fun interface ReceiptExtractor {
    suspend fun extract(
        ocrResult: OcrResult,
        candidates: List<ProductCandidate>,
    ): ExtractionResult
}

