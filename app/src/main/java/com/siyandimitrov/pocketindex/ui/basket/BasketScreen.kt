package com.siyandimitrov.pocketindex.ui.basket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siyandimitrov.pocketindex.data.local.CategoryEntity
import com.siyandimitrov.pocketindex.data.local.MerchantEntity
import com.siyandimitrov.pocketindex.data.local.ObservationSource
import com.siyandimitrov.pocketindex.data.local.UnitType
import com.siyandimitrov.pocketindex.ui.components.MiniLineChart
import com.siyandimitrov.pocketindex.ui.components.StatusPill
import com.siyandimitrov.pocketindex.ui.components.productEmoji
import com.siyandimitrov.pocketindex.ui.components.StatusTone
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BasketScreen(
    modifier: Modifier = Modifier,
    viewModel: BasketViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it) }
        if (state.message != null) viewModel.dismissMessage()
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.selectedProduct != null) {
            ProductDetail(
                state = state,
                onBack = viewModel::closeProduct,
                onChartMode = viewModel::selectChartMode,
                onSaveObservation = viewModel::saveManualObservation,
                onSaveProduct = viewModel::saveProduct,
                onMerge = viewModel::mergeInto,
                modifier = Modifier.padding(padding),
            )
        } else {
            ProductCatalogue(
                state = state,
                onSearch = viewModel::updateSearch,
                onCategory = viewModel::selectCategory,
                onStatus = viewModel::selectStatus,
                onProduct = viewModel::selectProduct,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProductCatalogue(
    state: BasketUiState,
    onSearch: (String) -> Unit,
    onCategory: (Long?) -> Unit,
    onStatus: (ProductStatusFilter) -> Unit,
    onProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Your basket", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Products and prices stored on this device",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search products") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item {
            FilterStrip(
                state = state,
                onCategory = onCategory,
                onStatus = onStatus,
            )
        }
        when {
            state.isLoading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.products.isEmpty() -> item {
                EmptyBasket(
                    title = "No products yet",
                    body = "Confirm a receipt or add a manual receipt to start your catalogue.",
                )
            }
            state.filteredProducts.isEmpty() -> item {
                EmptyBasket(
                    title = "No matching products",
                    body = "Try a different search or remove a filter.",
                )
            }
            else -> {
                items(state.filteredProducts, key = BasketProductUi::id) { product ->
                    ProductRow(product = product, onClick = { onProduct(product.id) })
                }
            }
        }
    }
}

@Composable
private fun FilterStrip(
    state: BasketUiState,
    onCategory: (Long?) -> Unit,
    onStatus: (ProductStatusFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.categoryId == null,
                onClick = { onCategory(null) },
                label = { Text("All categories") },
            )
            state.categories.forEach { category ->
                FilterChip(
                    selected = state.categoryId == category.id,
                    onClick = { onCategory(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProductStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.statusFilter == filter,
                    onClick = { onStatus(filter) },
                    label = {
                        Text(
                            when (filter) {
                                ProductStatusFilter.ALL -> "Any status"
                                ProductStatusFilter.FRESH -> "Fresh"
                                ProductStatusFilter.STALE -> "Stale"
                                ProductStatusFilter.FIXED_BASKET -> "Fixed basket"
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProductRow(product: BasketProductUi, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            ProductIcon(product.name, product.categoryName)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    product.categoryName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        product.observationCount == 0 ->
                            StatusPill("No prices", StatusTone.Neutral)
                        product.isFresh -> StatusPill("Fresh", StatusTone.Success)
                        else -> StatusPill("Stale", StatusTone.Attention)
                    }
                    if (product.isFixedBasketEligible) {
                        StatusPill("Fixed basket", StatusTone.Neutral)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    product.currentUnitPriceMicros?.let {
                        formatUnitPrice(it, product.unitType)
                    } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                )
                product.latestObservedAt?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductIcon(name: String? = null, categoryName: String? = null) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (name != null) {
            Text(productEmoji(name, categoryName), fontSize = 24.sp)
        } else {
            Icon(
                Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyBasket(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProductIcon()
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ProductDetail(
    state: BasketUiState,
    onBack: () -> Unit,
    onChartMode: (ProductChartMode) -> Unit,
    onSaveObservation: (String, String, String, Long?) -> Unit,
    onSaveProduct: (String, Long?, UnitType, String, String) -> Unit,
    onMerge: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = requireNotNull(state.selectedProduct)
    var showObservationDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showMergeDialog by rememberSaveable { mutableStateOf(false) }
    val chartHistory = detail.observations.asReversed().takeLast(12)
    val chartValues = chartHistory.map { observation ->
        when (state.chartMode) {
            ProductChartMode.UNIT_PRICE ->
                unitPriceDisplayValue(observation.unitPriceMicros.toDouble(), detail.product.unitType)
            ProductChartMode.PACK_SIZE -> observation.packSize
        }.toFloat()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to basket")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        detail.product.canonicalName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        detail.categoryName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit product")
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProductIcon(detail.product.canonicalName, detail.categoryName)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            detail.currentMeanUnitPriceMicros?.let {
                                formatUnitPrice(it, detail.product.unitType)
                            } ?: "No current price",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            if (detail.merchantPrices.isEmpty()) {
                                "Add a price to start tracking"
                            } else {
                                "Mean of ${detail.merchantPrices.size} carried-forward merchant price" +
                                    if (detail.merchantPrices.size == 1) "" else "s"
                            },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showObservationDialog = true },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(" Add price")
                }
                OutlinedButton(
                    onClick = { showMergeDialog = true },
                    enabled = !state.isSaving && state.products.size > 1,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Merge, contentDescription = null)
                    Text(" Merge")
                }
            }
        }
        item {
            SegmentControl(
                selected = state.chartMode,
                onSelected = onChartMode,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.chartMode == ProductChartMode.UNIT_PRICE) {
                        unitPriceAxisLabel(detail.product.unitType)
                    } else {
                        "Pack size (${unitShortLabel(detail.product.unitType)})"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (chartValues.size >= 2) {
                    MiniLineChart(
                        values = chartValues,
                        labels = chartLabels(chartHistory.map(ObservationUi::observedAt)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    InlineEmpty("At least two observations are needed for a chart.")
                }
            }
        }
        if (detail.packChanges.isNotEmpty()) {
            item { SectionTitle("Pack changes") }
            items(detail.packChanges, key = { "${it.observedAt}:${it.fromSize}:${it.toSize}" }) { event ->
                PackChangeRow(event, detail.product.unitType)
            }
        }
        item { SectionTitle("Current merchant prices") }
        if (detail.merchantPrices.isEmpty()) {
            item { InlineEmpty("No merchant prices recorded.") }
        } else {
            items(detail.merchantPrices, key = MerchantPriceUi::merchantName) { merchantPrice ->
                MerchantPriceRow(merchantPrice, detail.product.unitType)
            }
        }
        item { SectionTitle("Observation history") }
        if (detail.observations.isEmpty()) {
            item { InlineEmpty("No observations for this product yet.") }
        } else {
            items(detail.observations, key = ObservationUi::id) { observation ->
                ObservationRow(observation, detail.product.unitType)
            }
        }
        if (detail.aliases.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("Aliases")
                    Text(
                        detail.aliases.joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (showObservationDialog) {
        AddObservationDialog(
            product = detail,
            merchants = state.merchants,
            isSaving = state.isSaving,
            onDismiss = { showObservationDialog = false },
            onSave = { date, price, packSize, merchantId ->
                onSaveObservation(date, price, packSize, merchantId)
                showObservationDialog = false
            },
        )
    }
    if (showEditDialog) {
        EditProductDialog(
            detail = detail,
            categories = state.categories,
            isSaving = state.isSaving,
            onDismiss = { showEditDialog = false },
            onSave = { name, categoryId, unitType, packSize, aliases ->
                onSaveProduct(name, categoryId, unitType, packSize, aliases)
                showEditDialog = false
            },
        )
    }
    if (showMergeDialog) {
        MergeProductDialog(
            source = detail,
            products = state.products.filter { it.id != detail.product.id },
            isSaving = state.isSaving,
            onDismiss = { showMergeDialog = false },
            onMerge = {
                onMerge(it)
                showMergeDialog = false
            },
        )
    }
}

@Composable
private fun SegmentControl(
    selected: ProductChartMode,
    onSelected: (ProductChartMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ProductChartMode.entries.forEach { mode ->
            Surface(
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (mode == selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    if (mode == ProductChartMode.UNIT_PRICE) "Unit price" else "Pack size",
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = if (mode == selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PackChangeRow(event: PackChangeUi, unitType: UnitType) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (event.isShrinkflation) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (event.isShrinkflation) "Possible shrinkflation" else "Pack changed",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "${formatPackSize(event.fromSize, unitType)} → " +
                        formatPackSize(event.toSize, unitType),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(event.observedAt, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MerchantPriceRow(price: MerchantPriceUi, unitType: UnitType) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(price.merchantName, fontWeight = FontWeight.SemiBold)
                Text(
                    "Last seen ${price.observedAt} • ${formatMoney(price.shelfPriceMinor)} shelf",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                formatUnitPrice(price.unitPriceMicros.toDouble(), unitType),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ObservationRow(observation: ObservationUi, unitType: UnitType) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(observation.merchantName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${observation.observedAt} • ${sourceLabel(observation.source)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(formatMoney(observation.shelfPriceMinor), style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                "${formatUnitPrice(observation.unitPriceMicros.toDouble(), unitType)} • " +
                    formatPackSize(observation.packSize, unitType),
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun InlineEmpty(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(16.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AddObservationDialog(
    product: ProductDetailUi,
    merchants: List<MerchantEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long?) -> Unit,
) {
    var date by rememberSaveable {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date()))
    }
    var price by rememberSaveable { mutableStateOf("") }
    var packSize by rememberSaveable(product.product.id) {
        mutableStateOf(product.product.packSize?.toInputString().orEmpty())
    }
    var merchantId by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add price observation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Shelf price (£)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packSize,
                    onValueChange = { packSize = it },
                    label = { Text("Pack size (${unitShortLabel(product.product.unitType)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Merchant (optional)", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = merchantId == null,
                        onClick = { merchantId = null },
                        label = { Text("None") },
                    )
                    merchants.forEach { merchant ->
                        FilterChip(
                            selected = merchantId == merchant.id,
                            onClick = { merchantId = merchant.id },
                            label = { Text(merchant.name) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(date, price, packSize, merchantId) },
                enabled = !isSaving,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditProductDialog(
    detail: ProductDetailUi,
    categories: List<CategoryEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long?, UnitType, String, String) -> Unit,
) {
    var name by rememberSaveable(detail.product.id) { mutableStateOf(detail.product.canonicalName) }
    var categoryId by rememberSaveable(detail.product.id) { mutableStateOf(detail.product.categoryId) }
    var unitType by rememberSaveable(detail.product.id) { mutableStateOf(detail.product.unitType) }
    var packSize by rememberSaveable(detail.product.id) {
        mutableStateOf(detail.product.packSize?.toInputString().orEmpty())
    }
    var aliases by rememberSaveable(detail.product.id) {
        mutableStateOf(detail.aliases.joinToString("\n"))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit product") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Canonical name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { categoryId = category.id },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
                item {
                    Text("Unit type", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UnitType.entries.forEach { type ->
                            FilterChip(
                                selected = unitType == type,
                                onClick = { unitType = type },
                                label = { Text(unitTypeLabel(type)) },
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = packSize,
                        onValueChange = { packSize = it },
                        label = { Text("Typical pack size (${unitShortLabel(unitType)})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = aliases,
                        onValueChange = { aliases = it },
                        label = { Text("Aliases (one per line)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, categoryId, unitType, packSize, aliases) },
                enabled = !isSaving,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergeProductDialog(
    source: ProductDetailUi,
    products: List<BasketProductUi>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onMerge: (Long) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = products.filter { it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge duplicate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "“${source.product.canonicalName}” will be removed. Its aliases, prices, " +
                        "receipt lines, and recurring item will move to the product you keep.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find product to keep") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(matches, key = BasketProductUi::id) { product ->
                        FilterChip(
                            selected = selectedId == product.id,
                            onClick = { selectedId = product.id },
                            label = { Text("${product.name} • ${product.categoryName}") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let(onMerge) },
                enabled = !isSaving && selectedId != null,
            ) { Text("Merge and keep selected") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun sourceLabel(source: ObservationSource): String = when (source) {
    ObservationSource.RECEIPT -> "Receipt"
    ObservationSource.BILL -> "Bill"
    ObservationSource.MANUAL -> "Manual"
}

private fun unitTypeLabel(type: UnitType): String = when (type) {
    UnitType.MASS_G -> "Weight"
    UnitType.VOLUME_ML -> "Volume"
    UnitType.COUNT -> "Count"
    UnitType.SERVICE -> "Service"
}

private fun unitShortLabel(type: UnitType): String = when (type) {
    UnitType.MASS_G -> "g"
    UnitType.VOLUME_ML -> "ml"
    UnitType.COUNT -> "items"
    UnitType.SERVICE -> "period"
}

private fun unitPriceAxisLabel(type: UnitType): String = when (type) {
    UnitType.MASS_G -> "£ per kg"
    UnitType.VOLUME_ML -> "£ per litre"
    UnitType.COUNT -> "£ per item"
    UnitType.SERVICE -> "£ per period"
}

private fun unitPriceDisplayValue(unitPriceMicros: Double, type: UnitType): Double {
    val minorPerBaseUnit = unitPriceMicros / 1_000_000.0
    val minorPerDisplayUnit = when (type) {
        UnitType.MASS_G, UnitType.VOLUME_ML -> minorPerBaseUnit * 1_000.0
        UnitType.COUNT, UnitType.SERVICE -> minorPerBaseUnit
    }
    return minorPerDisplayUnit / 100.0
}

private fun formatUnitPrice(unitPriceMicros: Double, type: UnitType): String =
    "${formatPounds(unitPriceDisplayValue(unitPriceMicros, type))} / " +
        when (type) {
            UnitType.MASS_G -> "kg"
            UnitType.VOLUME_ML -> "litre"
            UnitType.COUNT -> "item"
            UnitType.SERVICE -> "period"
        }

private fun formatPackSize(size: Double, type: UnitType): String =
    "${NumberFormat.getNumberInstance(Locale.UK).apply { maximumFractionDigits = 2 }.format(size)} " +
        unitShortLabel(type)

private fun formatMoney(minor: Long): String = formatPounds(minor / 100.0)

private fun formatPounds(pounds: Double): String =
    NumberFormat.getCurrencyInstance(Locale.UK).format(pounds)

private fun chartLabels(dates: List<String>): List<String> {
    if (dates.isEmpty()) return emptyList()
    if (dates.size == 1) return dates
    val indexes = linkedSetOf(0, dates.lastIndex / 2, dates.lastIndex)
    return indexes.map { dates[it].drop(5) }
}

private fun Double.toInputString(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()
