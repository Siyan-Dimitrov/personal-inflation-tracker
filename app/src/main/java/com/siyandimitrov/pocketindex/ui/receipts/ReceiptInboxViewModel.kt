package com.siyandimitrov.pocketindex.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siyandimitrov.pocketindex.data.local.LocalDataLock
import com.siyandimitrov.pocketindex.data.local.PocketIndexDatabase
import com.siyandimitrov.pocketindex.data.local.PriceObservationEntity
import com.siyandimitrov.pocketindex.data.local.ReceiptListItem
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.ReceiptWithDetails
import com.siyandimitrov.pocketindex.data.repository.CatalogRepository
import com.siyandimitrov.pocketindex.data.repository.ObservationRepository
import com.siyandimitrov.pocketindex.data.repository.ReceiptRepository
import com.siyandimitrov.pocketindex.extraction.ReceiptExtractionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReceiptInboxUiState(
    val receipts: List<ReceiptListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
) {
    val processing: List<ReceiptListItem>
        get() = receipts.filter { it.status == ReceiptStatus.PENDING }
    val needsReview: List<ReceiptListItem>
        get() = receipts.filter { it.status == ReceiptStatus.NEEDS_REVIEW }
    val confirmed: List<ReceiptListItem>
        get() = receipts.filter { it.status == ReceiptStatus.CONFIRMED }
    val failed: List<ReceiptListItem>
        get() = receipts.filter { it.status == ReceiptStatus.FAILED }
    val queueCount: Int
        get() = processing.size + needsReview.size + failed.size
}

private data class InboxTransientState(
    val message: String? = null,
)

@HiltViewModel
class ReceiptInboxViewModel @Inject constructor(
    receiptRepository: ReceiptRepository,
    private val database: PocketIndexDatabase,
    private val extractionScheduler: ReceiptExtractionScheduler,
) : ViewModel() {
    private val transient = MutableStateFlow(InboxTransientState())

    val uiState: StateFlow<ReceiptInboxUiState> = combine(
        receiptRepository.observeReceipts()
            .map<List<ReceiptListItem>, Result<List<ReceiptListItem>>> { Result.success(it) }
            .catch { emit(Result.failure(it)) },
        transient,
    ) { result, transientState ->
        result.fold(
            onSuccess = {
                ReceiptInboxUiState(
                    receipts = it,
                    isLoading = false,
                    message = transientState.message,
                )
            },
            onFailure = {
                ReceiptInboxUiState(
                    isLoading = false,
                    error = "Receipts could not be loaded.",
                    message = transientState.message,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReceiptInboxUiState(),
    )

    fun retry(receiptId: Long) {
        val receipt = uiState.value.receipts.firstOrNull { it.id == receiptId } ?: return
        if (receipt.status == ReceiptStatus.CONFIRMED) return
        viewModelScope.launch {
            runCatching {
                database.receiptDao().updateStatus(receiptId, ReceiptStatus.PENDING)
                extractionScheduler.enqueue(receiptId, replaceFailedWork = true)
            }.onSuccess {
                transient.value = InboxTransientState("Receipt queued for extraction.")
            }.onFailure { error ->
                if (error is CancellationException) throw error
                transient.value = InboxTransientState("The receipt could not be retried.")
            }
        }
    }

    fun dismissMessage() {
        transient.update { it.copy(message = null) }
    }
}

data class ReceiptDetailUiState(
    val details: ReceiptWithDetails? = null,
    val productNames: Map<Long, String> = emptyMap(),
    val observationsByLineItem: Map<Long, PriceObservationEntity> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val isDeleted: Boolean = false,
)

@HiltViewModel
class ReceiptDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val receiptRepository: ReceiptRepository,
    catalogRepository: CatalogRepository,
    observationRepository: ObservationRepository,
    private val database: PocketIndexDatabase,
    private val extractionScheduler: ReceiptExtractionScheduler,
    private val localDataLock: LocalDataLock,
) : ViewModel() {
    private val receiptId = checkNotNull(savedStateHandle.get<Long>("receiptId")) {
        "Receipt detail requires a receiptId route argument."
    }
    private val transientMessage = MutableStateFlow<String?>(null)
    private val deleted = MutableStateFlow(false)

    val uiState: StateFlow<ReceiptDetailUiState> = combine(
        receiptRepository.observeReceipt(receiptId),
        catalogRepository.observeProducts(),
        observationRepository.observeAll(),
        transientMessage,
        deleted,
    ) { details, products, observations, message, isDeleted ->
        val lineItemIds = details?.lineItems?.mapTo(hashSetOf()) { it.id }.orEmpty()
        ReceiptDetailUiState(
            details = details,
            productNames = products.associate { it.product.id to it.product.canonicalName },
            observationsByLineItem = observations.mapNotNull { observation ->
                observation.receiptLineItemId
                    ?.takeIf(lineItemIds::contains)
                    ?.let { it to observation }
            }.toMap(),
            isLoading = false,
            error = if (details == null && !isDeleted) "This receipt could not be found." else null,
            message = message,
            isDeleted = isDeleted,
        )
    }.catch {
        emit(
            ReceiptDetailUiState(
                isLoading = false,
                error = "Receipt details could not be loaded.",
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReceiptDetailUiState(),
    )

    fun retry() {
        val receipt = uiState.value.details?.receipt ?: return
        if (receipt.status == ReceiptStatus.CONFIRMED) return
        viewModelScope.launch {
            runCatching {
                database.receiptDao().updateStatus(receiptId, ReceiptStatus.PENDING)
                extractionScheduler.enqueue(receiptId, replaceFailedWork = true)
            }.onSuccess {
                transientMessage.value = "Receipt queued for extraction."
            }.onFailure { error ->
                if (error is CancellationException) throw error
                transientMessage.value = "The receipt could not be retried."
            }
        }
    }

    fun deleteReceipt() {
        viewModelScope.launch {
            runCatching {
                extractionScheduler.cancel(receiptId)
                // Held under the data lock so a delete cannot interleave with a data reset or
                // with the extraction worker's own locked write for this receipt.
                localDataLock.withLock {
                    receiptRepository.deleteReceipt(receiptId)
                        ?.takeIf(String::isNotBlank)
                        ?.let { imagePath -> File(imagePath).delete() }
                }
            }.onSuccess {
                deleted.value = true
            }.onFailure { error ->
                if (error is CancellationException) throw error
                transientMessage.value = "The receipt could not be deleted."
            }
        }
    }
}
