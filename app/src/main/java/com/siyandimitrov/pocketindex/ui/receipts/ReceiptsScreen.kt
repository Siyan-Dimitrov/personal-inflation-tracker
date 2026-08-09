package com.siyandimitrov.pocketindex.ui.receipts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PriceCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.ui.components.StatusPill
import com.siyandimitrov.pocketindex.ui.components.StatusTone
import com.siyandimitrov.pocketindex.ui.components.productEmoji
import java.util.Locale

@Composable
fun ReceiptsScreen(
    modifier: Modifier = Modifier,
    startWithManualReceipt: Boolean = false,
) {
    val viewModel: ReceiptReviewViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showManualReceipt by remember(startWithManualReceipt) {
        mutableStateOf(startWithManualReceipt)
    }
    var showManualObservation by remember { mutableStateOf(false) }
    var showManualLine by remember { mutableStateOf(false) }
    var productEditorLineId by remember { mutableStateOf<Long?>(null) }

    if (showManualReceipt) {
        ManualReceiptDialog(
            onDismiss = { showManualReceipt = false },
            onSave = { merchant, date, subtotal, tax, total ->
                viewModel.createManualReceipt(merchant, date, subtotal, tax, total)
                showManualReceipt = false
            },
        )
    }
    if (showManualObservation) {
        ManualObservationDialog(
            products = state.productChoices,
            onDismiss = { showManualObservation = false },
            onSave = { productId, merchant, date, price, pack ->
                viewModel.addManualObservation(productId, merchant, date, price, pack)
                showManualObservation = false
            },
        )
    }
    if (showManualLine && state.receiptId != null) {
        ManualLineDialog(
            onDismiss = { showManualLine = false },
            onSave = { evidence, quantity, unitPrice, lineTotal ->
                viewModel.addManualLine(evidence, quantity, unitPrice, lineTotal)
                showManualLine = false
            },
        )
    }
    productEditorLineId?.let { lineId ->
        state.items.firstOrNull { it.id == lineId }?.let { line ->
            ProductEditorDialog(
                line = line,
                categories = state.categoryChoices,
                onDismiss = { productEditorLineId = null },
                onSave = { existingId, name, category, unitType, packSize ->
                    viewModel.saveProductForLine(
                        lineItemId = lineId,
                        existingProductId = existingId,
                        name = name,
                        categoryName = category,
                        unitType = unitType,
                        packSizeInput = packSize,
                    )
                    productEditorLineId = null
                },
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = if (state.isLiveReceipt) "Review receipt" else "Receipts",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = when {
                            state.isLoading -> "Loading local receipt data…"
                            state.queueCount > 0 -> "${state.queueCount} awaiting review"
                            else -> "No receipts awaiting review"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isLiveReceipt) {
                    Text(
                        text = state.totalMinor?.asPounds() ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }

        if (state.message != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::dismissMessage),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = state.message.orEmpty(),
                        modifier = Modifier.padding(15.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { showManualReceipt = true },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("Manual receipt")
                }
                OutlinedButton(
                    onClick = { showManualObservation = true },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                ) {
                    Icon(Icons.Rounded.PriceCheck, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("Manual price")
                }
            }
        }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (!state.isLiveReceipt) {
            item {
                EmptyReceiptState(unprocessedCount = state.unprocessedCount)
            }
        } else {
            if (state.receiptChoices.size > 1) {
                item {
                    ReceiptSelector(
                        selectedId = state.receiptId,
                        receipts = state.receiptChoices,
                        onSelected = viewModel::selectReceipt,
                    )
                }
            }

            item {
                ReceiptHeaderEditor(
                    state = state,
                    onMerchantChanged = viewModel::updateMerchant,
                    onDateChanged = viewModel::updatePurchasedAt,
                    onSubtotalChanged = viewModel::updateSubtotal,
                    onTaxChanged = viewModel::updateTax,
                    onTotalChanged = viewModel::updateTotal,
                )
            }

            item {
                ReconciliationCard(state)
            }

            items(state.items, key = ReviewLineItemUi::id) { line ->
                LiveReviewItemCard(
                    item = line,
                    products = state.productChoices,
                    enabled = state.isEditable && !state.isSaving,
                    onProductSelected = { viewModel.updateProduct(line.id, it) },
                    onQuantityChanged = { viewModel.updateQuantity(line.id, it) },
                    onUnitPriceChanged = { viewModel.updateUnitPrice(line.id, it) },
                    onLineTotalChanged = { viewModel.updateLineTotal(line.id, it) },
                    onPackSizeChanged = { viewModel.updatePackSize(line.id, it) },
                    onExcludedChanged = { viewModel.setExcluded(line.id, it) },
                    onEditProduct = { productEditorLineId = line.id },
                    onDelete = { viewModel.deleteLine(line.id) },
                )
            }

            item {
                OutlinedButton(
                    onClick = { showManualLine = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.isEditable && !state.isSaving,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(7.dp))
                    Text("Add missing receipt line")
                }
            }

            item {
                Button(
                    onClick = viewModel::confirmReceipt,
                    enabled = state.confirmEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = when {
                            state.isSaving -> "Saving…"
                            state.confirmEnabled ->
                                "Confirm ${state.includedItems.size} product lines"
                            state.items.isEmpty() -> "Add at least one product line"
                            else -> "Resolve lines and totals to continue"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyReceiptState(unprocessedCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("Nothing to review", style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (unprocessedCount > 0) {
                    "$unprocessedCount scans have no extracted lines yet. You can add a receipt manually."
                } else {
                    "Scan a receipt, add one manually, or record an individual shelf price."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReceiptSelector(
    selectedId: Long?,
    receipts: List<ReceiptChoiceUi>,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = receipts.firstOrNull { it.id == selectedId }?.label ?: "Choose receipt"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Receipt awaiting review",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(label, Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                receipts.forEach { receipt ->
                    DropdownMenuItem(
                        text = {
                            Text("${receipt.label} · ${receipt.lineItemCount} lines")
                        },
                        onClick = {
                            onSelected(receipt.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptHeaderEditor(
    state: ReceiptReviewUiState,
    onMerchantChanged: (String) -> Unit,
    onDateChanged: (String) -> Unit,
    onSubtotalChanged: (String) -> Unit,
    onTaxChanged: (String) -> Unit,
    onTotalChanged: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Receipt details", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.merchantInput,
                    onValueChange = onMerchantChanged,
                    modifier = Modifier.weight(1.2f),
                    label = { Text("Merchant") },
                    singleLine = true,
                    enabled = state.isEditable,
                    isError = state.merchantInput.isBlank(),
                )
                OutlinedTextField(
                    value = state.purchasedAtInput,
                    onValueChange = onDateChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Date") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    enabled = state.isEditable,
                    isError = !state.purchasedAtInput.isValidIsoDate(),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(
                    value = state.subtotalInput,
                    onValueChange = onSubtotalChanged,
                    label = "Subtotal",
                    modifier = Modifier.weight(1f),
                    enabled = state.isEditable,
                    optional = true,
                )
                MoneyField(
                    value = state.taxInput,
                    onValueChange = onTaxChanged,
                    label = "Tax",
                    modifier = Modifier.weight(1f),
                    enabled = state.isEditable,
                    optional = true,
                )
                MoneyField(
                    value = state.totalInput,
                    onValueChange = onTotalChanged,
                    label = "Total",
                    modifier = Modifier.weight(1f),
                    enabled = state.isEditable,
                )
            }
        }
    }
}

@Composable
private fun ReconciliationCard(state: ReceiptReviewUiState) {
    val attentionCount = state.items.count { !it.isComplete }
    val valid = state.reconciliationValid && attentionCount == 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (valid) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                RoundedCornerShape(16.dp),
            )
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (valid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    valid -> "Ready to confirm"
                    attentionCount > 0 -> "$attentionCount lines need attention"
                    else -> "Totals need attention"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when {
                    state.reconciliationDifferenceMinor == null ->
                        "Enter valid prices and receipt totals."
                    state.reconciliationValid ->
                        "Product lines reconcile within the allowed £0.02."
                    else ->
                        "Product lines differ from the subtotal by " +
                            absMinor(state.reconciliationDifferenceMinor).asPounds() + "."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveReviewItemCard(
    item: ReviewLineItemUi,
    products: List<ProductChoiceUi>,
    enabled: Boolean,
    onProductSelected: (Long) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onUnitPriceChanged: (String) -> Unit,
    onLineTotalChanged: (String) -> Unit,
    onPackSizeChanged: (String) -> Unit,
    onExcludedChanged: (Boolean) -> Unit,
    onEditProduct: () -> Unit,
    onDelete: () -> Unit,
) {
    var productMenuExpanded by remember(item.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(item.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        productEmoji(item.productName ?: item.rawText),
                        fontSize = 24.sp,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Original receipt text", style = MaterialTheme.typography.labelMedium)
                    Text(item.rawText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    StatusPill(
                        text = when {
                            item.excluded -> "Excluded non-product"
                            item.isComplete -> "Ready"
                            else -> "Needs attention"
                        },
                        tone = if (item.isComplete) StatusTone.Success else StatusTone.Attention,
                    )
                }
                Text(
                    text = item.lineTotalMinor?.asPounds() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = item.excluded,
                    onCheckedChange = onExcludedChanged,
                    enabled = enabled,
                )
                Column(Modifier.weight(1f)) {
                    Text("Exclude this line", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Use for coupons, discounts, totals, bags, or other non-product text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showDeleteConfirmation = true }, enabled = enabled) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete this line",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text("Delete this line?") },
                    text = {
                        Text(
                            "“${item.rawText}” will be removed from this receipt. " +
                                "Use this for misread text; use Exclude for real " +
                                "coupons and discounts, so the totals still reconcile.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirmation = false
                                onDelete()
                            },
                        ) { Text("Delete") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
                    },
                )
            }

            if (!item.excluded) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Canonical product",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { productMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Text(
                                text = item.productName ?: if (products.isEmpty()) {
                                    "No products yet — create the first one"
                                } else {
                                    "Choose a product"
                                },
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                color = if (item.productId == null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        DropdownMenu(
                            expanded = productMenuExpanded,
                            onDismissRequest = { productMenuExpanded = false },
                        ) {
                            products.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text("${product.name} · ${product.categoryName}") },
                                    onClick = {
                                        onProductSelected(product.id)
                                        productMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = onEditProduct, enabled = enabled) {
                        Icon(
                            if (item.productId == null) Icons.Rounded.Add else Icons.Rounded.Edit,
                            contentDescription = null,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (item.productId == null) {
                                "Create product from this line"
                            } else {
                                "Correct product, category, unit, or pack"
                            },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        value = item.quantityInput,
                        onValueChange = onQuantityChanged,
                        label = "Quantity",
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                    )
                    MoneyField(
                        value = item.unitPriceInput,
                        onValueChange = onUnitPriceChanged,
                        label = "Unit price",
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        allowNegative = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoneyField(
                        value = item.lineTotalInput,
                        onValueChange = onLineTotalChanged,
                        label = "Line total",
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        allowNegative = true,
                    )
                    NumberField(
                        value = item.packSizeInput,
                        onValueChange = onPackSizeChanged,
                        label = item.unitType.packSizeLabel(),
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                    )
                }
                if (item.categoryName != null) {
                    Text(
                        "${item.categoryName} · ${item.unitType.displayName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualReceiptDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var merchant by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var subtotal by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a manual receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { if (it.length <= 10) date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                )
                MoneyField(subtotal, { subtotal = it }, "Subtotal (optional)")
                MoneyField(tax, { tax = it }, "Tax (optional)")
                MoneyField(total, { total = it }, "Total")
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(merchant, date, subtotal, tax, total) }) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManualLineDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var evidence by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var unitPrice by remember { mutableStateOf("") }
    var lineTotal by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add missing line") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This description becomes immutable receipt evidence.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = evidence,
                    onValueChange = { evidence = it },
                    label = { Text("Line description") },
                    singleLine = true,
                )
                NumberField(quantity, { quantity = it }, "Quantity")
                MoneyField(unitPrice, { unitPrice = it }, "Unit price", allowNegative = true)
                MoneyField(lineTotal, { lineTotal = it }, "Line total", allowNegative = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(evidence, quantity, unitPrice, lineTotal) }) {
                Text("Add line")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProductEditorDialog(
    line: ReviewLineItemUi,
    categories: List<CategoryChoiceUi>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, UnitType, String) -> Unit,
) {
    var name by remember(line.id, line.productId) {
        mutableStateOf(line.productName ?: line.rawText.take(120))
    }
    var category by remember(line.id, line.productId) {
        mutableStateOf(line.categoryName ?: categories.firstOrNull()?.name.orEmpty())
    }
    var unitType by remember(line.id, line.productId) {
        mutableStateOf(line.unitType ?: UnitType.COUNT)
    }
    var packSize by remember(line.id, line.productId) {
        mutableStateOf(line.packSizeInput.ifBlank { "1" })
    }
    var categoryMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (line.productId == null) "Create product" else "Correct product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Canonical name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    supportingText = { Text("A new category is created if needed.") },
                    singleLine = true,
                )
                if (categories.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { categoryMenu = true }) {
                            Text("Choose existing category")
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false },
                        ) {
                            categories.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choice.name) },
                                    onClick = {
                                        category = choice.name
                                        categoryMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { unitMenu = true }) {
                        Text("Unit: ${unitType.displayName()}")
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        UnitType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    unitType = type
                                    unitMenu = false
                                },
                            )
                        }
                    }
                }
                NumberField(
                    value = packSize,
                    onValueChange = { packSize = it },
                    label = unitType.packSizeLabel(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(line.productId, name, category, unitType, packSize) },
            ) {
                Text("Save product")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManualObservationDialog(
    products: List<ProductChoiceUi>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String, String) -> Unit,
) {
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var productMenu by remember { mutableStateOf(false) }
    var merchant by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var packSize by remember { mutableStateOf("") }
    val selected = products.firstOrNull { it.id == selectedProductId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an individual price") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (products.isEmpty()) {
                    Text(
                        "Create a product from a receipt line before recording its price.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { productMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Text(
                                selected?.name ?: "Choose product",
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = productMenu,
                            onDismissRequest = { productMenu = false },
                        ) {
                            products.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product.name) },
                                    onClick = {
                                        selectedProductId = product.id
                                        packSize = product.packSize?.toInputText().orEmpty()
                                        productMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { if (it.length <= 10) date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                )
                MoneyField(price, { price = it }, "Shelf price")
                NumberField(
                    packSize,
                    { packSize = it },
                    selected?.unitType.packSizeLabel(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedProductId, merchant, date, price, packSize) },
                enabled = products.isNotEmpty(),
            ) {
                Text("Add price")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optional: Boolean = false,
    allowNegative: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            val pattern = if (allowNegative) {
                Regex("""-?\d{0,7}(?:[.,]\d{0,2})?""")
            } else {
                Regex("""\d{0,7}(?:[.,]\d{0,2})?""")
            }
            if (it.isEmpty() || it.matches(pattern)) {
                onValueChange(it)
            }
        },
        modifier = modifier,
        label = { Text(label) },
        prefix = { Text("£") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = !optional && (
            if (allowNegative) value.toSignedMinorUnits() else value.toMinorUnits()
            ) == null,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (it.isEmpty() || it.matches(Regex("""\d{0,7}(?:[.,]\d{0,3})?"""))) {
                onValueChange(it)
            }
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = (value.toReceiptNumber() ?: 0.0) <= 0.0,
    )
}

private fun UnitType?.packSizeLabel(): String = when (this) {
    UnitType.MASS_G -> "Pack size (g)"
    UnitType.VOLUME_ML -> "Pack size (ml)"
    UnitType.COUNT -> "Pack count"
    UnitType.SERVICE -> "Service units"
    null -> "Pack size"
}

private fun UnitType?.displayName(): String = when (this) {
    UnitType.MASS_G -> "Weight (grams)"
    UnitType.VOLUME_ML -> "Volume (millilitres)"
    UnitType.COUNT -> "Count"
    UnitType.SERVICE -> "Service"
    null -> "Choose unit"
}

private fun Double.toInputText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Long.asPounds(): String = "£%.2f".format(Locale.UK, this / 100.0)

private fun absMinor(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(value)
