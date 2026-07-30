package com.siyandimitrov.pocketindex.ui.receipts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siyandimitrov.pocketindex.ui.components.QuantityControl
import com.siyandimitrov.pocketindex.ui.components.StatusPill
import com.siyandimitrov.pocketindex.ui.components.StatusTone

private data class ReviewItem(
    val name: String,
    val price: String,
    val icon: ImageVector,
    val status: String,
    val statusTone: StatusTone,
    val packSizes: List<String>,
    val selectedPack: String,
)

private val sampleItems = listOf(
    ReviewItem(
        name = "Whole milk",
        price = "£1.55",
        icon = Icons.Rounded.LocalDrink,
        status = "Low confidence",
        statusTone = StatusTone.Attention,
        packSizes = listOf("1 L", "1.8 L", "2 L"),
        selectedPack = "2 L",
    ),
    ReviewItem(
        name = "Pasta 500g",
        price = "£0.95",
        icon = Icons.Rounded.Restaurant,
        status = "Confirmed",
        statusTone = StatusTone.Success,
        packSizes = emptyList(),
        selectedPack = "",
    ),
    ReviewItem(
        name = "Tomatoes",
        price = "£1.20",
        icon = Icons.Rounded.Inventory2,
        status = "Check pack size",
        statusTone = StatusTone.Attention,
        packSizes = listOf("Loose", "500 g", "1 kg"),
        selectedPack = "500 g",
    ),
)

@Composable
fun ReceiptsScreen(modifier: Modifier = Modifier) {
    val viewModel: ReceiptReviewViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attentionCount = if (state.isLiveReceipt) {
        state.items.count {
            it.productId == null ||
                (it.confidence ?: 0.0) < 0.75 ||
                !it.isComplete
        }
    } else {
        sampleItems.count { it.statusTone == StatusTone.Attention }
    }
    val merchantName = if (state.isLiveReceipt) state.merchantName else "Tesco"
    val totalMinor = if (state.isLiveReceipt) state.totalMinor else 3_482L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Review receipt",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = "$merchantName · Today",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(totalMinor.asPounds(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Total",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "$attentionCount items need attention",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (!state.isLiveReceipt || state.reconciliationValid) {
                            "Subtotal and total reconcile"
                        } else {
                            "Prices do not yet match the receipt total"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        if (state.isLiveReceipt) {
            items(state.items.size) { index ->
                val item = state.items[index]
                LiveReviewItemCard(
                    item = item,
                    products = state.productChoices,
                    onProductSelected = { viewModel.updateProduct(item.id, it) },
                    onQuantityChanged = { viewModel.updateQuantity(item.id, it) },
                    onPackSizeChanged = { viewModel.updatePackSize(item.id, it) },
                )
            }
        } else {
            items(sampleItems.size) { index ->
                ReviewItemCard(sampleItems[index])
            }
        }

        item {
            Button(
                onClick = viewModel::confirmReceipt,
                enabled = !state.isLiveReceipt || state.confirmEnabled,
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
                        state.isLiveReceipt && !state.confirmEnabled -> "Match items to continue"
                        state.isLiveReceipt -> "Confirm ${state.items.size} items"
                        else -> "Confirm 6 items"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun Long.asPounds(): String = "£%.2f".format(this / 100.0)

@Composable
private fun LiveReviewItemCard(
    item: ReviewLineItemUi,
    products: List<ProductChoiceUi>,
    onProductSelected: (Long) -> Unit,
    onQuantityChanged: (String) -> Unit,
    onPackSizeChanged: (String) -> Unit,
) {
    var productMenuExpanded by remember(item.id) { mutableStateOf(false) }
    val needsAttention = item.productId == null ||
        (item.confidence ?: 0.0) < 0.75 ||
        !item.isComplete
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
                    Icon(
                        imageVector = Icons.Rounded.Inventory2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.rawText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(5.dp))
                    StatusPill(
                        text = if (needsAttention) "Needs attention" else "Matched",
                        tone = if (needsAttention) StatusTone.Attention else StatusTone.Success,
                    )
                }
                Text(
                    text = item.lineTotalMinor.asPounds(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Product match",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { productMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = item.productName ?: "Choose a product",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = if (item.productName == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    DropdownMenu(
                        expanded = productMenuExpanded,
                        onDismissRequest = { productMenuExpanded = false },
                    ) {
                        products.forEach { product ->
                            DropdownMenuItem(
                                text = { Text(product.name) },
                                onClick = {
                                    onProductSelected(product.id)
                                    productMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = item.quantityInput,
                    onValueChange = onQuantityChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text("Quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = item.packSizeInput,
                    onValueChange = onPackSizeChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(item.unitType.packSizeLabel()) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
    }
}

private fun com.siyandimitrov.pocketindex.data.local.UnitType?.packSizeLabel(): String =
    when (this) {
        com.siyandimitrov.pocketindex.data.local.UnitType.MASS_G -> "Pack size (g)"
        com.siyandimitrov.pocketindex.data.local.UnitType.VOLUME_ML -> "Pack size (ml)"
        com.siyandimitrov.pocketindex.data.local.UnitType.COUNT -> "Pack count"
        com.siyandimitrov.pocketindex.data.local.UnitType.SERVICE -> "Service units"
        null -> "Pack size"
    }

@Composable
private fun ReviewItemCard(item: ReviewItem) {
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
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(5.dp))
                    StatusPill(item.status, item.statusTone)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = item.price,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Item options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Quantity",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuantityControl("1")
            }

            if (item.packSizes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Pack size",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.packSizes.forEach { packSize ->
                            val selected = packSize == item.selectedPack
                            Text(
                                text = packSize,
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
