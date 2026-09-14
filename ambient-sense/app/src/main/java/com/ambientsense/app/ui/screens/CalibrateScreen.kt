package com.ambientsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.data.LabelEntity
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.AmbientCard
import com.ambientsense.app.ui.components.FieldLabel
import com.ambientsense.app.ui.components.LevelBar
import com.ambientsense.app.ui.components.SectionTitle
import com.ambientsense.app.ui.theme.AmbientPalette
import com.ambientsense.app.util.formatCount
import com.ambientsense.app.util.formatTimeShort
import java.util.Locale
import kotlin.math.abs

/**
 * Where the human closes the loop.
 *
 * You count what you can actually see, press submit, and the online model updates the
 * *correction* it applies to the physics heuristic. Do it a handful of times in a place
 * and the app starts agreeing with you in that place.
 */
@Composable
fun CalibrateScreen(vm: AmbientViewModel) {
    val fusion by vm.fusion.collectAsStateWithLifecycle()
    val noise by vm.noise.collectAsStateWithLifecycle()
    val labels by vm.labels.collectAsStateWithLifecycle()
    val summary by vm.modelSummary.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    var people by remember { mutableIntStateOf(0) }
    var vehicles by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var noiseTruth by remember { mutableFloatStateOf(noise.leqDba ?: 50f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // --------------------------------------------------------- ground truth
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("What do you actually see?")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "The app currently estimates ${formatCount(fusion.people)} people and " +
                        "${formatCount(fusion.vehicles)} vehicles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Stepper("People", people, onValueChange = { people = it })
                Spacer(Modifier.height(10.dp))
                Stepper("Vehicles", vehicles, onValueChange = { vehicles = it })
                Spacer(Modifier.height(14.dp))
                FieldLabel("Noise level")
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = noiseTruth,
                        onValueChange = { noiseTruth = it },
                        valueRange = 30f..110f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = AmbientPalette.NoiseColor,
                            activeTrackColor = AmbientPalette.NoiseColor
                        )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f dB", noiseTruth),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(58.dp),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Context (optional)") },
                    placeholder = { Text("bus stop, 6 people waiting…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        vm.submitLabel(people, vehicles, noiseTruth, note.ifBlank { null })
                        note = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Submit ground truth")
                }
                if (!running) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Sensing is stopped — labels are most useful while the radios are running.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---------------------------------------------------------- model state
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Learned calibration")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    ModelStat("labels", "${summary.people.observations}", Modifier.weight(1f))
                    ModelStat(
                        "MAE",
                        String.format(Locale.US, "%.2f", summary.people.mae),
                        Modifier.weight(1f)
                    )
                    ModelStat(
                        "R²",
                        summary.people.r2?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                        Modifier.weight(1f)
                    )
                    ModelStat(
                        "drift",
                        String.format(Locale.US, "%+.2f", summary.people.recentBias),
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = when {
                        summary.people.observations == 0 ->
                            "Running on the physics baseline. Each label you submit teaches the model " +
                                "how far off the baseline is in this environment."
                        summary.people.observations < 6 ->
                            "Collecting labels… at 6 the learned correction starts blending in."
                        else ->
                            "Model influence ${(vm.modelSummary.value.people.observations)
                                .coerceAtMost(30) * 100 / 30}% of maximum. Keep labelling to sharpen it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (summary.people.observations > 0) {
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Feature weights · people")
                    Spacer(Modifier.height(8.dp))
                    val maxAbs = summary.people.weights.maxOfOrNull { abs(it.second) }?.toFloat() ?: 1f
                    summary.people.weights
                        .sortedByDescending { abs(it.second) }
                        .forEach { (name, weight) ->
                            WeightRow(name, weight.toFloat(), maxAbs)
                        }
                    Spacer(Modifier.height(10.dp))
                    FieldLabel("Feature weights · vehicles")
                    Spacer(Modifier.height(8.dp))
                    val maxAbsV = summary.vehicles.weights.maxOfOrNull { abs(it.second) }?.toFloat() ?: 1f
                    summary.vehicles.weights
                        .sortedByDescending { abs(it.second) }
                        .forEach { (name, weight) ->
                            WeightRow(name, weight.toFloat(), maxAbsV)
                        }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.resetCalibration() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset calibration")
                }
            }
        }

        // ------------------------------------------------------------- history
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Label history · ${labels.size}")
                Spacer(Modifier.height(8.dp))
                if (labels.isEmpty()) {
                    Text(
                        "No labels yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    labels.take(12).forEach { label ->
                        LabelRow(label) { vm.deleteLabel(label.id) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { if (value > 0) onValueChange(value - 1) },
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Fewer", modifier = Modifier.size(16.dp))
        }
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .width(46.dp)
                .padding(vertical = 2.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { if (value < 999) onValueChange(value + 1) },
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "More", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ModelStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        FieldLabel(label)
    }
}

@Composable
private fun WeightRow(name: String, weight: Float, maxAbs: Float) {
    val frac = (abs(weight) / maxAbs.coerceAtLeast(1e-6f)).coerceIn(0f, 1f)
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format(Locale.US, "%+.2f", weight),
                style = MaterialTheme.typography.bodySmall,
                color = if (weight >= 0) AmbientPalette.GoodColor else AmbientPalette.DangerColor
            )
        }
        Spacer(Modifier.height(3.dp))
        LevelBar(
            fraction = frac,
            color = if (weight >= 0) AmbientPalette.GoodColor else AmbientPalette.DangerColor,
            modifier = Modifier.fillMaxWidth(),
            height = 4.dp
        )
    }
}

@Composable
private fun LabelRow(label: LabelEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${label.peopleTruth} people · ${label.vehicleTruth} vehicles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(formatTimeShort(label.t))
                    append(" · app said ")
                    append(String.format(Locale.US, "%.1f", label.peopleEstimate))
                    append(" / ")
                    append(String.format(Locale.US, "%.1f", label.vehicleEstimate))
                    if (!label.note.isNullOrBlank()) append(" · ${label.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete label",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
