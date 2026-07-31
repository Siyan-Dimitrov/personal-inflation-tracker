package com.siyandimitrov.pocketindex.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.siyandimitrov.pocketindex.domain.EpochDay
import com.siyandimitrov.pocketindex.domain.HeadlineRateKind
import com.siyandimitrov.pocketindex.domain.InflationDashboardCalculation
import com.siyandimitrov.pocketindex.domain.ProductContribution
import com.siyandimitrov.pocketindex.ui.components.CoverageRing
import com.siyandimitrov.pocketindex.ui.components.InflationColor
import com.siyandimitrov.pocketindex.ui.components.MiniLineChart
import com.siyandimitrov.pocketindex.ui.components.PrivacyChip
import com.siyandimitrov.pocketindex.ui.components.StatusPill
import com.siyandimitrov.pocketindex.ui.components.StatusTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun OverviewScreen(
    isScanning: Boolean,
    onScanReceipt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OverviewViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 22.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Pocket Index", style = MaterialTheme.typography.headlineLarge)
                PrivacyChip()
            }
        }

        when (val currentState = state) {
            OverviewUiState.Loading -> item { LoadingCard() }
            is OverviewUiState.Empty -> item {
                MessageCard(
                    title = currentState.title,
                    explanation = currentState.explanation,
                )
            }
            is OverviewUiState.BuildingBaseBasket -> item {
                BuildingCard(currentState)
            }
            is OverviewUiState.Error -> item {
                MessageCard(
                    title = "Index unavailable",
                    explanation = currentState.explanation,
                    isError = true,
                )
            }
            is OverviewUiState.Ready -> {
                val dashboard = currentState.dashboard
                item { Headline(dashboard) }
                item { IndexChart(dashboard) }
                item { CoverageCard(dashboard) }
                item { CategoryExplanation(dashboard) }
                item { ProductExplanation(dashboard.productContributions) }
                if (dashboard.staleProducts.isNotEmpty()) {
                    item { StaleExplanation(dashboard) }
                }
                item { BasketComparison(dashboard) }
            }
        }

        item {
            ScanButton(
                isScanning = isScanning,
                onScanReceipt = onScanReceipt,
            )
        }
    }
}

@Composable
private fun Headline(dashboard: InflationDashboardCalculation.Ready) {
    val rate = dashboard.headlineRate
    Column {
        Text(
            text = "Your inflation",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = rate?.percent?.asSignedPercent() ?: "Not enough history",
                style = if (rate == null) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.displayLarge
                },
                color = rate?.percent?.changeColor() ?: MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(
                text = when (rate?.kind) {
                    HeadlineRateKind.YEAR_ON_YEAR -> "Year on year"
                    HeadlineRateKind.ANNUALISED_EARLY_ESTIMATE -> "Early estimate"
                    null -> "Building history"
                },
                tone = if (rate?.kind == HeadlineRateKind.YEAR_ON_YEAR) {
                    StatusTone.Success
                } else {
                    StatusTone.Attention
                },
            )
        }
        Text(
            text = when (rate?.kind) {
                HeadlineRateKind.YEAR_ON_YEAR ->
                    "Change since ${rate.comparison.asOf.asMonthYear()}"
                HeadlineRateKind.ANNUALISED_EARLY_ESTIMATE ->
                    "Annualised from ${rate.comparison.asOf.asMonthYear()}; this will settle as history grows."
                null -> "A second monthly point is needed before a rate can be shown."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IndexChart(dashboard: InflationDashboardCalculation.Ready) {
    if (dashboard.fixedSeries.size < 2) {
        MessageCard(
            title = "Monthly chart is building",
            explanation = "The first point is ready. Another point will appear as time and prices accumulate.",
        )
        return
    }
    val visible = dashboard.fixedSeries.takeLast(6)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Fixed-basket index",
            style = MaterialTheme.typography.titleMedium,
        )
        MiniLineChart(
            values = visible.map { it.index.toFloat() },
            labels = visible.map { it.asOf.asMonthLabel() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CoverageCard(dashboard: InflationDashboardCalculation.Ready) {
    val coverage = dashboard.freshCoveragePercent.coerceIn(0.0, 100.0)
    MetricSurface {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CoverageRing(percent = coverage.roundToInt())
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Fresh coverage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${coverage.roundToInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
            Text(
                text = if (dashboard.staleProducts.isEmpty()) {
                    "All basket prices are current"
                } else {
                    "${dashboard.staleProducts.size} stale"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryExplanation(dashboard: InflationDashboardCalculation.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Why did it change?", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Percentage-point contributions to the displayed headline. Weights come from your base spending unless overridden.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MetricSurface {
            Column {
                dashboard.categories.forEachIndexed { index, category ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(10.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${(category.weight * 100.0).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${category.changePercent.asSignedPercent()} within category",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = category.contributionPercentagePoints.asSignedPoints(),
                            style = MaterialTheme.typography.titleMedium,
                            color = category.contributionPercentagePoints.changeColor(),
                        )
                    }
                    if (index < dashboard.categories.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductExplanation(contributions: List<ProductContribution>) {
    val positives = contributions.filter { it.contributionPercentagePoints > 0 }.take(3)
    val negatives = contributions
        .filter { it.contributionPercentagePoints < 0 }
        .sortedBy { it.contributionPercentagePoints }
        .take(3)
    if (positives.isEmpty() && negatives.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Biggest product effects", style = MaterialTheme.typography.titleLarge)
        MetricSurface {
            Column {
                (positives + negatives).forEachIndexed { index, contribution ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (contribution.contributionPercentagePoints >= 0) {
                                Icons.AutoMirrored.Rounded.TrendingUp
                            } else {
                                Icons.AutoMirrored.Rounded.TrendingDown
                            },
                            contentDescription = null,
                            tint = contribution.contributionPercentagePoints.changeColor(),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(contribution.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${contribution.categoryName} · ${contribution.changePercent.asSignedPercent()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = contribution.contributionPercentagePoints.asSignedPoints(),
                            color = contribution.contributionPercentagePoints.changeColor(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (index < positives.size + negatives.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaleExplanation(dashboard: InflationDashboardCalculation.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Coverage needs attention", style = MaterialTheme.typography.titleLarge)
        MetricSurface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Prices older than 90 days are carried forward, but marked stale so confidence is visible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dashboard.staleProducts.take(5).forEach { product ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "  ${product.name}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "${product.ageDays} days",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BasketComparison(dashboard: InflationDashboardCalculation.Ready) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fixed versus chained", style = MaterialTheme.typography.titleLarge)
        MetricSurface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricLine("Fixed basket", "%.2f".format(Locale.UK, dashboard.currentFixedIndex))
                val chained = dashboard.chainedSeries?.lastOrNull()?.index
                MetricLine(
                    "Chained basket",
                    chained?.let { "%.2f".format(Locale.UK, it) } ?: "After 12 months",
                )
                dashboard.fixedToChainedGapPercent?.let { gap ->
                    MetricLine("Substitution gap", gap.asSignedPercent())
                }
                Text(
                    text = "The fixed index holds your original basket constant. The chained index updates the basket annually; their gap can reflect substitution. Changing shops alone is not treated as deflation because prices are averaged across merchants.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BuildingCard(state: OverviewUiState.BuildingBaseBasket) {
    MetricSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Building your base basket", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = buildString {
                    append("${state.observationCount} confirmed price")
                    if (state.observationCount != 1) append("s")
                    append(". Products need two observations; bills need one.")
                    if (state.daysRemaining > 0) {
                        append(" The configured base window has ${state.daysRemaining} days remaining.")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingCard() {
    MetricSurface {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text("Calculating from your saved prices…")
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    explanation: String,
    isError: Boolean = false,
) {
    MetricSurface {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@Composable
private fun ScanButton(
    isScanning: Boolean,
    onScanReceipt: () -> Unit,
) {
    Button(
        onClick = onScanReceipt,
        enabled = !isScanning,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Rounded.CameraAlt, contentDescription = null)
        }
        Spacer(Modifier.padding(horizontal = 5.dp))
        Text(
            if (isScanning) "Reading receipt…" else "Scan receipt",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun Double.changeColor(): Color =
    if (this < 0.0) MaterialTheme.colorScheme.secondary else InflationColor

private fun Double.asSignedPercent(): String =
    "%+.1f%%".format(Locale.UK, this)

private fun Double.asSignedPoints(): String =
    "%+.2f pp".format(Locale.UK, this)

private fun EpochDay.asMonthLabel(): String = format("MMM")

private fun EpochDay.asMonthYear(): String = format("MMM yyyy")

private fun EpochDay.format(pattern: String): String =
    SimpleDateFormat(pattern, Locale.UK).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(Math.multiplyExact(value, 86_400_000L)))
