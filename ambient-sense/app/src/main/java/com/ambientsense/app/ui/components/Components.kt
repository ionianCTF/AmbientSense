package com.ambientsense.app.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ambientsense.app.ui.theme.AmbientPalette
import kotlin.math.abs

// --------------------------------------------------------------------- surfaces

@Composable
fun AmbientCard(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = if (tonal) cs.outlineVariant else cs.outline.copy(alpha = 0.85f),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        color = if (tonal) cs.surfaceVariant else cs.surface,
        content = content
    )
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

// ---------------------------------------------------------------- metric tiles

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String? = null,
    accent: Color = AmbientPalette.BookCloth,
    modifier: Modifier = Modifier,
    hint: String? = null,
    spark: List<Float>? = null
) {
    AmbientCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            FieldLabel(label)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (unit != null) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            if (spark != null && spark.size > 1) {
                Spacer(Modifier.height(8.dp))
                Sparkline(values = spark, color = accent, modifier = Modifier.fillMaxWidth().height(26.dp))
            }
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            textAlign = TextAlign.End
        )
    }
}

// -------------------------------------------------------------------- charts

@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    baseline: Float? = null
) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val span = (max - min).let { if (abs(it) < 1e-6f) 1f else it }
        val w = size.width
        val h = size.height
        fun px(i: Int) = Offset(
            x = (i.toFloat() / (values.size - 1)) * w,
            y = h - ((values[i] - min) / span) * h
        )
        val path = Path().apply {
            moveTo(0f, h)
            for (i in values.indices) {
                val p = px(i)
                lineTo(p.x, p.y)
            }
        }
        if (fill) {
            val fillPath = Path().apply {
                addPath(path)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f))
                )
            )
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = 2.5f)
        )
        if (baseline != null && baseline in min..max) {
            val y = h - ((baseline - min) / span) * h
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            )
        }
    }
}

/**
 * Radial gauge: the arc shows the value against [max], the inner ring shows how much
 * the estimator trusts itself.
 */
@Composable
fun RadialGauge(
    value: Float,
    max: Float,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    confidence: Float = 0f,
    sublabel: String? = null
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val stroke = 14.dp.toPx()
        val confStroke = 4.dp.toPx()
        val radius = (size.minDimension - stroke) / 2f
        val topLeft = Offset(
            (size.width - radius * 2) / 2f,
            (size.height - radius * 2) / 2f
        )
        // track
        drawArc(
            color = track,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        // value arc
        val frac = (value / max).coerceIn(0f, 1f)
        drawArc(
            brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color)),
            startAngle = 135f,
            sweepAngle = 270f * frac,
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke)
        )
        // confidence ring
        drawArc(
            color = color.copy(alpha = 0.5f),
            startAngle = 135f,
            sweepAngle = 270f * confidence.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(
                topLeft.x + stroke / 2 - confStroke / 2,
                topLeft.y + stroke / 2 - confStroke / 2
            ),
            size = Size(radius * 2 - stroke + confStroke, radius * 2 - stroke + confStroke),
            style = Stroke(width = confStroke)
        )
    }
}

@Composable
fun LevelBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.height(height)) {
        drawRoundRect(color = track, size = size)
        drawRoundRect(
            color = color,
            size = Size(size.width * fraction.coerceIn(0f, 1f), size.height)
        )
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
    )
}

// ------------------------------------------------------------------ selection

@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primary else cs.surface
    val fg = if (selected) cs.onPrimary else cs.onSurface
    val border = if (selected) cs.primary else cs.outline
    Box(
        modifier = modifier
            .selectable(selected = selected, onClick = onClick)
            .border(1.dp, border, RoundedCornerShape(percent = 50))
            .background(bg, RoundedCornerShape(percent = 50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun ConfidencePill(confidence: Float, text: String, modifier: Modifier = Modifier) {
    val color = when {
        confidence >= 0.7f -> AmbientPalette.GoodColor
        confidence >= 0.45f -> AmbientPalette.Kraft
        else -> AmbientPalette.SlateLight
    }
    Row(
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color = color, size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
