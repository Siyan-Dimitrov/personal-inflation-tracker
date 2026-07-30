package com.siyandimitrov.pocketindex.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siyandimitrov.pocketindex.ui.components.CoverageRing
import com.siyandimitrov.pocketindex.ui.components.InflationColor
import com.siyandimitrov.pocketindex.ui.components.MiniLineChart
import com.siyandimitrov.pocketindex.ui.components.PrivacyChip
import com.siyandimitrov.pocketindex.ui.components.StatusPill
import com.siyandimitrov.pocketindex.ui.components.StatusTone

@Composable
fun OverviewScreen(
    onScanReceipt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                Text(
                    text = "Pocket Index",
                    style = MaterialTheme.typography.headlineLarge,
                )
                PrivacyChip()
            }
        }

        item {
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
                        text = "+6.8%",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    StatusPill("Early estimate", StatusTone.Attention)
                }
            }
        }

        item {
            MiniLineChart(
                values = listOf(1.2f, 3.1f, 4.2f, 5.4f, 6.0f, 6.8f),
                labels = listOf("Nov", "Dec", "Jan", "Feb", "Mar", "Apr"),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CoverageRing(percent = 82)
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Coverage",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "82%",
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(
                                text = "fresh",
                                modifier = Modifier.padding(bottom = 4.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Text(
                        text = "Updated today",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "What’s driving it",
                    style = MaterialTheme.typography.titleLarge,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        DriverRow(Icons.Rounded.LocalGroceryStore, "Groceries", "+8.4%")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DriverRow(Icons.Rounded.HomeWork, "Home energy", "+4.1%")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        DriverRow(Icons.Rounded.DirectionsCar, "Transport", "+3.6%")
                    }
                }
            }
        }

        item {
            Button(
                onClick = onScanReceipt,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 5.dp))
                Text("Scan receipt", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DriverRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = InflationColor,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

