package com.ambientsense.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ambientsense.app.model.SenseRadius
import com.ambientsense.app.ui.theme.AmbientPalette
import kotlin.math.roundToInt

/**
 * The sensing-radius control.
 *
 * Every option carries the accuracy measured for it in `validation/radius.py`, so
 * the user is choosing a number *and* being told what that number costs — rather
 * than dragging a slider and hoping.
 */

/** Compact chip row: pick fast, see the accuracy of what you picked underneath. */
@Composable
fun RadiusChipRow(
    selectedMeters: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SenseRadius.OPTIONS.forEach { option ->
            RadiusChip(
                label = "${option.meters.roundToInt()} m",
                selected = option.meters == selectedMeters,
                onClick = { onSelect(option.meters) }
            )
        }
    }
}

@Composable
private fun RadiusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .selectable(selected = selected, onClick = onClick)
            .border(1.dp, if (selected) cs.primary else cs.outline, RoundedCornerShape(50))
            .background(if (selected) cs.primary else cs.surface, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.onPrimary else cs.onSurface)
    }
}

/**
 * One-line accuracy promise for the selected radius, given how many labels the user
 * has taken *at this radius*.
 */
@Composable
fun RadiusAccuracyLine(
    meters: Float,
    labelsAtRadius: Int,
    modifier: Modifier = Modifier,
    detailed: Boolean = false
) {
    val option = SenseRadius.nearest(meters)
    val fresh = SenseRadius.expectedMae(meters, 0)
    val calibrated = SenseRadius.expectedMae(meters, 25)
    val now = SenseRadius.expectedMae(meters, labelsAtRadius)
    val cs = MaterialTheme.colorScheme

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "±${fmt1(now)} people",
                style = MaterialTheme.typography.titleSmall,
                color = cs.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (labelsAtRadius > 0) "with your $labelsAtRadius labels"
                else "before you calibrate",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Expected error at ${meters.roundToInt()} m: ±${fmt1(fresh)} fresh → " +
                "±${fmt1(calibrated)} after ~25 labels · the radios sample about 1 in " +
                "${(1f / option.coverage.coerceAtLeast(0.02f)).roundToInt()} of the people " +
                "directly, the rest is inferred",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        if (detailed) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Right within ±1 person ${pct(option.withinOne)} of the time once " +
                    "calibrated (${pct(option.withinOneFresh)} before) · " +
                    "catches ${pct(option.vehicleRecall)} of the vehicles passing through",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

/**
 * Full picker: one row per radius with both accuracy numbers side by side, so the
 * trade-off is visible without tapping through each option.
 */
@Composable
fun RadiusOptionList(
    selectedMeters: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SenseRadius.OPTIONS.forEach { option ->
            RadiusOptionRow(option = option, selected = option.meters == selectedMeters,
                onClick = { onSelect(option.meters) })
        }
    }
}

@Composable
private fun RadiusOptionRow(
    option: SenseRadius.Option,
    selected: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val border = if (selected) cs.primary else cs.outlineVariant
    val bg = if (selected) cs.primaryContainer.copy(alpha = 0.35f) else cs.surface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(bg)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${option.meters.roundToInt()} m",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
                modifier = Modifier.width(52.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(option.name, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                if (option.recommended) {
                    Text("recommended", style = MaterialTheme.typography.labelSmall,
                        color = AmbientPalette.Olive)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "±${fmt1(option.maeFresh)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Text("fresh", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Text("→", style = MaterialTheme.typography.bodySmall,
                color = cs.outline, modifier = Modifier.padding(horizontal = 8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "±${fmt1(option.maeCalibrated)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.primary
                )
                Text("calibrated", style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant)
            }
        }
        if (selected) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = option.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat("within ±1", pct(option.withinOne), Modifier.weight(1f))
                MiniStat("crowd sampled directly", pct(option.coverage), Modifier.weight(1f))
                MiniStat("vehicles caught", pct(option.vehicleRecall), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Measured in simulation (10 scenes × 5 min per radius), not a field " +
                    "trial. Your Accuracy tab replaces these with this phone's real error.",
                style = MaterialTheme.typography.labelSmall,
                color = cs.outline,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun fmt1(v: Float): String = (kotlin.math.round(v * 10f) / 10f).let {
    if (it == kotlin.math.floor(it)) it.toInt().toString()
    else String.format(java.util.Locale.US, "%.1f", it)
}

private fun pct(v: Float): String = "${(v * 100).roundToInt()}%"
