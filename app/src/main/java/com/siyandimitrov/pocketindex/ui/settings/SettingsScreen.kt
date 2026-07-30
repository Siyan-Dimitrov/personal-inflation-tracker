package com.siyandimitrov.pocketindex.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siyandimitrov.pocketindex.data.local.RecurringCadence

private data class RecurringBill(
    val source: RecurringBillUi,
    val name: String,
    val provider: String,
    val price: String,
    val due: String,
    val icon: ImageVector,
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: RecurringBillsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBillEditor by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<RecurringBillUi?>(null) }
    var pendingRemoval by remember { mutableStateOf<RecurringBillUi?>(null) }
    val displayBills = state.bills.map { bill ->
        RecurringBill(
            source = bill,
            name = bill.name,
            provider = bill.providerLabel(),
            price = bill.priceMinor.asPounds(wholePounds = true),
            due = "Updated ${bill.lastUpdated}",
            icon = bill.icon(),
        )
    }
    val monthlyTotal = state.monthlyTotalMinor.asPounds(wholePounds = true)

    if (showBillEditor) {
        BillEditorDialog(
            bill = editingBill,
            onDismiss = { showBillEditor = false },
            onSave = { name, price, cadence ->
                viewModel.saveBill(editingBill, name, price, cadence)
                showBillEditor = false
            },
            onRemove = editingBill?.let { bill ->
                {
                    showBillEditor = false
                    pendingRemoval = bill
                }
            },
        )
    }
    pendingRemoval?.let { bill ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove ${bill.name}?") },
            text = {
                Text(
                    "This removes the bill from your active monthly total. " +
                        "Existing price history is kept for your index.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeBill(bill)
                        pendingRemoval = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Recurring bills", style = MaterialTheme.typography.headlineLarge)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(monthlyTotal, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = " / month",
                    modifier = Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
            ) {
                if (displayBills.isEmpty()) {
                    Text(
                        text = "No recurring bills yet",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        displayBills.forEachIndexed { index, bill ->
                            BillRow(
                                bill = bill,
                                onClick = {
                                    editingBill = bill.source
                                    showBillEditor = true
                                },
                            )
                            if (index < displayBills.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    editingBill = null
                    showBillEditor = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("  Add bill", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "Included in your personal index",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        item {
            Text(
                text = "Data & privacy",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        Icons.Rounded.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Export to CSV",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Receipts, products, and observations",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun BillEditorDialog(
    bill: RecurringBillUi?,
    onDismiss: () -> Unit,
    onSave: (String, String, RecurringCadence) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by remember(bill?.recurringItemId) {
        mutableStateOf(bill?.name.orEmpty())
    }
    var price by remember(bill?.recurringItemId) {
        mutableStateOf(bill?.priceMinor?.asEditorPounds().orEmpty())
    }
    var cadence by remember(bill?.recurringItemId) {
        mutableStateOf(bill?.cadence ?: RecurringCadence.MONTHLY)
    }
    var cadenceMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bill == null) "Add recurring bill" else "Edit recurring bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Monthly price (£)") },
                    singleLine = true,
                )
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cadenceMenuExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = "Cadence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = cadence.displayName(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = cadenceMenuExpanded,
                        onDismissRequest = { cadenceMenuExpanded = false },
                    ) {
                        RecurringCadence.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName()) },
                                onClick = {
                                    cadence = option
                                    cadenceMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, price, cadence) },
                enabled = name.isNotBlank() && price.toMinorUnitsOrNull() != null,
            ) {
                Text(if (bill == null) "Add bill" else "Save")
            }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Text(" Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

private fun RecurringBillUi.providerLabel(): String = when (name.lowercase()) {
    "electricity" -> "British Gas"
    "broadband", "home broadband" -> "BT"
    "council tax" -> "Manchester City Council"
    else -> cadence.displayName()
}

private fun RecurringBillUi.icon(): ImageVector = when {
    name.contains("electric", ignoreCase = true) -> Icons.Rounded.ElectricBolt
    name.contains("broadband", ignoreCase = true) ||
        name.contains("internet", ignoreCase = true) -> Icons.Rounded.Wifi
    else -> Icons.Rounded.AccountBalance
}

private fun RecurringCadence.displayName(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)

private fun Long.asPounds(wholePounds: Boolean): String =
    if (wholePounds && this % 100L == 0L) {
        "£${this / 100L}"
    } else {
        "£%.2f".format(java.util.Locale.UK, this / 100.0)
    }

private fun Long.asEditorPounds(): String =
    if (this % 100L == 0L) {
        (this / 100L).toString()
    } else {
        "%.2f".format(java.util.Locale.UK, this / 100.0)
    }

@Composable
private fun BillRow(
    bill: RecurringBill,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = bill.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = bill.provider,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = bill.due,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = bill.price,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = bill.source.cadence.displayName(),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(11.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
