package com.siyandimitrov.pocketindex.ui.receipts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siyandimitrov.pocketindex.data.local.ReceiptListItem
import com.siyandimitrov.pocketindex.data.local.ReceiptStatus
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.ui.components.StatusPill
import com.siyandimitrov.pocketindex.ui.components.StatusTone
import java.io.File

@Composable
fun ReceiptInboxScreen(
    onReceiptSelected: (Long) -> Unit,
    onScanReceipt: () -> Unit,
    onAddManualReceipt: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> InboxError(
            message = state.error.orEmpty(),
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Receipts", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            if (state.queueCount == 0) "Everything is up to date"
                            else "${state.queueCount} ${if (state.queueCount == 1) "receipt" else "receipts"} need action",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = onAddManualReceipt) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Manual")
                    }
                }
            }

            state.message?.let { message ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = viewModel::dismissMessage),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(message, Modifier.padding(14.dp))
                    }
                }
            }

            if (state.receipts.isEmpty()) {
                item {
                    EmptyInbox(
                        onScanReceipt = onScanReceipt,
                        onAddManualReceipt = onAddManualReceipt,
                    )
                }
            } else {
                receiptSection(
                    title = "Processing",
                    subtitle = "Extraction continues even if you close the app.",
                    receipts = state.processing,
                    onReceiptSelected = onReceiptSelected,
                    onRetry = viewModel::retry,
                )
                receiptSection(
                    title = "Needs review",
                    subtitle = "Correct highlighted fields, then reconcile and confirm.",
                    receipts = state.needsReview,
                    onReceiptSelected = onReceiptSelected,
                    onRetry = viewModel::retry,
                )
                receiptSection(
                    title = "Failed",
                    subtitle = "Retry from the saved original—no rescan needed.",
                    receipts = state.failed,
                    onReceiptSelected = onReceiptSelected,
                    onRetry = viewModel::retry,
                )
                receiptSection(
                    title = "Confirmed",
                    subtitle = "Stored receipts and the observations they produced.",
                    receipts = state.confirmed,
                    onReceiptSelected = onReceiptSelected,
                    onRetry = viewModel::retry,
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.receiptSection(
    title: String,
    subtitle: String,
    receipts: List<ReceiptListItem>,
    onReceiptSelected: (Long) -> Unit,
    onRetry: (Long) -> Unit,
) {
    if (receipts.isEmpty()) return
    item(key = "header-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(receipts, key = ReceiptListItem::id) { receipt ->
        ReceiptInboxRow(
            receipt = receipt,
            onClick = { onReceiptSelected(receipt.id) },
            onRetry = { onRetry(receipt.id) },
        )
    }
}

@Composable
private fun ReceiptInboxRow(
    receipt: ReceiptListItem,
    onClick: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(10.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    receipt.merchantName ?: "Unknown merchant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${receipt.purchasedAt} · ${receipt.lineItemCount} ${if (receipt.lineItemCount == 1) "line" else "lines"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(receipt.status.statusLabel(), receipt.status.statusTone())
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(receipt.totalMinor.asPounds(), style = MaterialTheme.typography.titleMedium)
                if (receipt.status == ReceiptStatus.FAILED) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text("Retry")
                    }
                } else {
                    Text(
                        if (receipt.status == ReceiptStatus.NEEDS_REVIEW) "Review ›" else "Open ›",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyInbox(
    onScanReceipt: () -> Unit,
    onAddManualReceipt: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
            Text("No receipts yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Scan your first receipt or enter one manually. Nothing is uploaded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScanReceipt, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Text("Scan receipt")
            }
            OutlinedButton(onClick = onAddManualReceipt, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("Add manually")
            }
        }
    }
}

@Composable
private fun InboxError(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
        Text(message, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ReceiptDetailScreen(
    onBack: () -> Unit,
    onReview: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details = state.details
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Receipt detail", style = MaterialTheme.typography.headlineMedium)
            }
        }
        when {
            state.isLoading -> item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> item { InboxError(state.error.orEmpty()) }
            details != null -> {
                val receipt = details.receipt
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    details.merchant?.name ?: "Unknown merchant",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(receipt.totalMinor.asPounds(), style = MaterialTheme.typography.titleLarge)
                            }
                            Text(receipt.purchasedAt)
                            StatusPill(receipt.status.statusLabel(), receipt.status.statusTone())
                            Text(
                                "Subtotal ${receipt.subtotalMinor?.asPounds() ?: "—"} · Tax ${receipt.taxMinor?.asPounds() ?: "—"} · Total ${receipt.totalMinor.asPounds()}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    ReceiptEvidenceImage(receipt.imagePath)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("OCR evidence", style = MaterialTheme.typography.titleLarge)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                receipt.ocrText?.takeIf(String::isNotBlank)
                                    ?: if (receipt.status == ReceiptStatus.PENDING) {
                                        "OCR is still processing."
                                    } else {
                                        "No OCR text was produced."
                                    },
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                item {
                    Text("Line items", style = MaterialTheme.typography.titleLarge)
                }
                items(details.lineItems, key = { it.id }) { line ->
                    val observation = state.observationsByLineItem[line.id]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                line.productId?.let(state.productNames::get) ?: line.rawText,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (line.productId != null) {
                                Text(
                                    "Original: ${line.rawText}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "Qty ${line.quantity.asQuantity()} · Unit ${line.unitPriceMinor.asPounds()} · Line ${line.lineTotalMinor.asPounds()}",
                            )
                            if (observation != null) {
                                Text(
                                    "Observation: ${observation.shelfPriceMinor.asPounds()} · pack size ${observation.packSize.asQuantity()} · ${observation.observedAt}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }

                state.message?.let { message -> item { Text(message) } }
                item {
                    when (receipt.status) {
                        ReceiptStatus.NEEDS_REVIEW -> Button(
                            onClick = { onReview(receipt.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Review and correct")
                        }
                        ReceiptStatus.FAILED -> Button(
                            onClick = viewModel::retry,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Text("Retry extraction")
                        }
                        ReceiptStatus.PENDING -> Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Extraction is running in the background.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = viewModel::retry,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                                Text("Restart extraction")
                            }
                        }
                        ReceiptStatus.CONFIRMED -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptEvidenceImage(imagePath: String) {
    if (imagePath.isBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Original image", style = MaterialTheme.typography.titleLarge)
            Text(
                "This receipt was entered manually, so no image was captured.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val bitmap = remember(imagePath) { decodeSampledBitmap(imagePath, maxDimension = 1_600) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Original image", style = MaterialTheme.typography.titleLarge)
        if (bitmap == null) {
            Text(
                "The original image is unavailable.",
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Original receipt image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun decodeSampledBitmap(path: String, maxDimension: Int): Bitmap? {
    if (!File(path).isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    )
}

private fun ReceiptStatus.statusLabel(): String = when (this) {
    ReceiptStatus.PENDING -> "Processing"
    ReceiptStatus.NEEDS_REVIEW -> "Needs review"
    ReceiptStatus.CONFIRMED -> "Confirmed"
    ReceiptStatus.FAILED -> "Failed"
}

private fun ReceiptStatus.statusTone(): StatusTone = when (this) {
    ReceiptStatus.PENDING -> StatusTone.Neutral
    ReceiptStatus.NEEDS_REVIEW -> StatusTone.Attention
    ReceiptStatus.CONFIRMED -> StatusTone.Success
    ReceiptStatus.FAILED -> StatusTone.Attention
}

private fun Long.asPounds(): String = "£%.2f".format(this / 100.0)

private fun Double.asQuantity(): String =
    if (this % 1.0 == 0.0) toLong().toString() else "%.3f".format(this).trimEnd('0').trimEnd('.')
