package com.siyandimitrov.pocketindex.extraction

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.siyandimitrov.pocketindex.data.local.LocalDataLock
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.ExtractedLineItem
import com.siyandimitrov.pocketindex.data.repository.ReceiptExtraction
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.suggestions.ProductSuggestionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray

/**
 * Replays OCR and deterministic extraction from the immutable private receipt image.
 *
 * Each receipt uses a unique WorkManager name, so a navigation event or a repeated enqueue cannot
 * create parallel extraction jobs. Confirmed receipts are never rewritten.
 */
@HiltWorker
class ReceiptExtractionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val database: PocketIndexDatabase,
    private val localDataLock: LocalDataLock,
    private val receiptRepository: ReceiptRepository,
    private val catalogRepository: CatalogRepository,
    private val ocrService: ReceiptOcrService,
    private val extractor: ReceiptExtractor,
    private val suggestionService: ProductSuggestionService,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val receiptId = inputData.getLong(KEY_RECEIPT_ID, MISSING_RECEIPT_ID)
        if (receiptId == MISSING_RECEIPT_ID) {
            return Result.failure(errorData("The extraction job did not include a receipt."))
        }

        return try {
            val receiptDetails = receiptRepository.observeReceipt(receiptId).first()
                ?: return Result.failure(errorData("Receipt $receiptId no longer exists."))
            val receipt = receiptDetails.receipt
            if (receipt.status == ReceiptStatus.CONFIRMED) {
                return Result.success(resultData(receiptId))
            }

            val imageFile = File(receipt.imagePath)
            check(imageFile.isFile) { "The original receipt image is unavailable." }
            publishProgress(PROGRESS_READING, "Reading the printed text")
            val ocrResult = ocrService.recognise(Uri.fromFile(imageFile))
            val candidates = catalogRepository.observeProducts().first().map { productWithCategory ->
                val product = productWithCategory.product
                ProductCandidate(
                    id = product.id,
                    canonicalName = product.canonicalName,
                    aliases = product.aliases.toAliasList(),
                    packSize = product.packSize,
                    baseUnit = product.unitType.toExtractionBaseUnit(),
                )
            }
            publishProgress(PROGRESS_PARSING, "Understanding the receipt")
            val extraction = extractor.extract(ocrResult, candidates)
            check(extraction.lineItems.isNotEmpty()) {
                "No purchasable line items could be read from this receipt."
            }

            val merchantName = extraction.merchantName
                ?.trim()
                ?.take(MAX_MERCHANT_LENGTH)
                ?.takeIf(String::isNotBlank)
            // A receipt dated after today is an OCR misread, so the scan date stays in place.
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
            val purchasedAt = extraction.purchasedAt?.takeIf { it <= today }
            val suggestedProductIds = suggestProductIds(extraction, candidates)
            val lineItems = extraction.lineItems.map { item ->
                val suggestedId = item.productMatch?.productId
                    ?: suggestedProductIds[item.description]
                ExtractedLineItem(
                    rawText = item.rawText,
                    quantity = item.quantity,
                    unitPriceMinor = (item.unitPriceMinor ?: item.lineTotalMinor).toLong(),
                    lineTotalMinor = item.lineTotalMinor.toLong(),
                    productId = suggestedId,
                    matchConfidence = when {
                        item.productMatch != null -> item.productMatch.confidence
                        suggestedId != null -> SUGGESTION_CONFIDENCE
                        else -> null
                    },
                )
            }
            publishProgress(PROGRESS_SAVING, "Saving the receipt")
            val calculatedTotal = lineItems.sumOf(ExtractedLineItem::lineTotalMinor)
            // The merchant is created in its own transaction, so both writes are held under the
            // lock and re-check the receipt: a data reset during OCR must not leave a merchant
            // behind for a receipt that no longer exists.
            localDataLock.withLock {
                checkNotNull(database.receiptDao().getById(receiptId)) {
                    "Receipt $receiptId no longer exists."
                }
                val merchantId = merchantName?.let { name ->
                    catalogRepository.getOrCreateMerchant(name)
                }
                receiptRepository.saveExtraction(
                    receiptId = receiptId,
                    extraction = ReceiptExtraction(
                        merchantId = merchantId,
                        purchasedAt = purchasedAt,
                        subtotalMinor = extraction.totals.subtotalMinor?.toLong(),
                        taxMinor = extraction.totals.taxMinor?.toLong(),
                        totalMinor = extraction.totals.totalMinor?.toLong() ?: calculatedTotal,
                        ocrText = extraction.rawOcrText,
                        status = ReceiptStatus.NEEDS_REVIEW,
                        lineItems = lineItems,
                    ),
                )
            }
            Result.success(resultData(receiptId))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            database.receiptDao().getById(receiptId)
                ?.takeUnless { it.status == ReceiptStatus.CONFIRMED }
                ?.let { database.receiptDao().updateStatus(receiptId, ReceiptStatus.FAILED) }
            Result.failure(
                errorData(
                    error.message
                        ?.takeIf(String::isNotBlank)
                        ?.take(MAX_ERROR_LENGTH)
                        ?: "Receipt extraction failed.",
                ),
            )
        }
    }

    /**
     * Asks the optional cloud model to match the lines the deterministic matcher missed.
     *
     * Best-effort by design: no key, no candidates, or any failure returns an empty map and the
     * lines simply stay unmatched for manual review. Suggestions carry [SUGGESTION_CONFIDENCE],
     * below the confident-match threshold, so the review screen always presents them for
     * confirmation.
     */
    private suspend fun suggestProductIds(
        extraction: ExtractionResult,
        candidates: List<ProductCandidate>,
    ): Map<String, Long> {
        if (!suggestionService.isEnabled || candidates.isEmpty()) return emptyMap()
        val unmatched = extraction.lineItems
            .filter { it.productMatch == null && !isPromotionLine(it.rawText) }
            .map { it.description }
            .distinct()
        if (unmatched.isEmpty()) return emptyMap()
        publishProgress(PROGRESS_SUGGESTING, "Matching products with AI")
        val candidatesByName = candidates.associateBy { it.canonicalName }
        return suggestionService.suggestProducts(unmatched, candidates.map { it.canonicalName })
            .mapNotNull { (description, name) ->
                candidatesByName[name]?.let { description to it.id }
            }
            .toMap()
    }

    /** Stage progress for the inbox: a single network or OCR call has no true percentage. */
    private suspend fun publishProgress(fraction: Float, step: String) {
        setProgress(
            workDataOf(
                KEY_PROGRESS_FRACTION to fraction,
                KEY_PROGRESS_STEP to step,
            ),
        )
    }

    companion object {
        const val KEY_RECEIPT_ID = "receipt_id"
        const val KEY_ERROR = "extraction_error"
        const val KEY_PROGRESS_FRACTION = "progress_fraction"
        const val KEY_PROGRESS_STEP = "progress_step"
        private const val PROGRESS_READING = 0.15f
        private const val PROGRESS_PARSING = 0.5f
        private const val PROGRESS_SUGGESTING = 0.7f
        private const val PROGRESS_SAVING = 0.9f
        private const val MISSING_RECEIPT_ID = -1L
        private const val MAX_MERCHANT_LENGTH = 80
        private const val MAX_ERROR_LENGTH = 240
        private const val SUGGESTION_CONFIDENCE = 0.70

        fun uniqueName(receiptId: Long): String = "receipt-extraction-$receiptId"

        private fun resultData(receiptId: Long): Data =
            Data.Builder().putLong(KEY_RECEIPT_ID, receiptId).build()

        private fun errorData(message: String): Data =
            Data.Builder().putString(KEY_ERROR, message).build()
    }
}

@Singleton
class ReceiptExtractionScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val applicationContext = context.applicationContext

    suspend fun enqueue(receiptId: Long, replaceFailedWork: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ReceiptExtractionWorker>()
            .setInputData(
                Data.Builder()
                    .putLong(ReceiptExtractionWorker.KEY_RECEIPT_ID, receiptId)
                    .build(),
            )
            .addTag(ReceiptExtractionWorker.uniqueName(receiptId))
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            ReceiptExtractionWorker.uniqueName(receiptId),
            if (replaceFailedWork) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        ).awaitCommit()
    }

    fun cancel(receiptId: Long) {
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(ReceiptExtractionWorker.uniqueName(receiptId))
    }
}

private suspend fun Operation.awaitCommit() {
    suspendCancellableCoroutine { continuation ->
        val future = result
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    .onFailure { error ->
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
            },
            DirectExecutor,
        )
    }
}

private object DirectExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
}

private fun String.toAliasList(): List<String> = runCatching {
    val json = JSONArray(this)
    buildList(json.length()) {
        repeat(json.length()) { index ->
            json.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }
}.getOrDefault(emptyList())

private fun UnitType.toExtractionBaseUnit(): BaseUnit = when (this) {
    UnitType.MASS_G -> BaseUnit.MASS_G
    UnitType.VOLUME_ML -> BaseUnit.VOLUME_ML
    UnitType.COUNT, UnitType.SERVICE -> BaseUnit.COUNT
}
