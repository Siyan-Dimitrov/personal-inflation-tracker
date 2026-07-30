package com.siyandimitrov.pocketindex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siyandimitrov.pocketindex.ui.theme.Aubergine
import com.siyandimitrov.pocketindex.ui.theme.Coral
import com.siyandimitrov.pocketindex.ui.theme.Sage
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.continuous
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape

@Composable
fun PrivacyChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Sage, CircleShape),
        )
        Text(
            text = "On device",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val background = when (tone) {
        StatusTone.Attention -> MaterialTheme.colorScheme.errorContainer
        StatusTone.Success -> MaterialTheme.colorScheme.secondaryContainer
        StatusTone.Neutral -> MaterialTheme.colorScheme.primaryContainer
    }
    val foreground = when (tone) {
        StatusTone.Attention -> MaterialTheme.colorScheme.error
        StatusTone.Success -> MaterialTheme.colorScheme.secondary
        StatusTone.Neutral -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = text,
        modifier = modifier
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
    )
}

enum class StatusTone {
    Attention,
    Success,
    Neutral,
}

@Composable
fun MetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}

@Composable
fun MiniLineChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = Aubergine,
) {
    require(values.size >= 2)
    val modelProducer = remember { CartesianChartModelProducer() }
    val pointComponent = rememberShapeComponent(
        fill = fill(lineColor),
        shape = CorneredShape.Pill,
    )
    val line = LineCartesianLayer.rememberLine(
        fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
        stroke = LineCartesianLayer.LineStroke.continuous(thickness = 2.5.dp),
        pointProvider = LineCartesianLayer.PointProvider.single(
            LineCartesianLayer.point(
                component = pointComponent,
                size = 7.dp,
            ),
        ),
    )
    LaunchedEffect(values) {
        modelProducer.runTransaction {
            lineSeries { series(values) }
        }
    }
    Column(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CoverageRing(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.outline
    Box(modifier = modifier.size(54.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(48.dp)) {
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Sage,
                startAngle = -90f,
                sweepAngle = 360f * percent / 100f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                style = Stroke(7.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
fun QuantityControl(
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("−", style = MaterialTheme.typography.titleLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Text("+", style = MaterialTheme.typography.titleLarge)
    }
}

val InflationColor = Coral
