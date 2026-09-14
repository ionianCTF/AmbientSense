package com.ambientsense.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.model.SenseRadius
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.AmbientCard
import com.ambientsense.app.ui.components.FieldLabel
import com.ambientsense.app.ui.components.LevelBar
import com.ambientsense.app.ui.components.RadiusChipRow
import com.ambientsense.app.ui.components.SectionTitle
import com.ambientsense.app.ui.theme.AmbientPalette
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Accuracy you can audit.
 *
 * Every label stores what the app was showing *before* that label was folded into the
 * model, so the stored pairs are a one-step-ahead (prequential) error series: the
 * honest accuracy of this phone, in these places, with this much calibration. Nothing
 * here is fitted after the fact.
 */
@Composable
fun ValidationScreen(vm: AmbientViewModel) {
    val v by vm.validation.collectAsStateWithLifecycle()
    val summary by vm.modelSummary.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        RadiusExpectationCard(vm)

        if (v.labels == 0) {
            AmbientCard(Modifier.fillMaxWidth(), tonal = true) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "No labels yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This screen turns your ground-truth counts into an accuracy report. " +
                            "Submit a few counts on the Calibrate tab and the numbers appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // -------------------------------------------------------- headline
            AmbientCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Accuracy on ${v.labels} labels")
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        BigStat("%.2f".format(Locale.US, v.people.mae), "MAE people", Modifier.weight(1f))
                        BigStat("%.2f".format(Locale.US, v.people.rmse), "RMSE", Modifier.weight(1f))
                        BigStat(
                            "%+.2f".format(Locale.US, v.people.bias), "bias",
                            Modifier.weight(1f),
                            accent = if (v.people.bias >= 0) AmbientPalette.Clay else AmbientPalette.Stone
                        )
                        BigStat(
                            "%.0f%%".format(Locale.US, 100 * v.people.withinOne), "within ±1",
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    FieldLabel("Calibrated vs uncalibrated rules")
                    Spacer(Modifier.height(6.dp))
                    val max = maxOf(v.peopleRules.mae, v.people.mae, 0.1)
                    MetricBar("rules only", v.peopleRules.mae / max,
                        "%.2f".format(Locale.US, v.peopleRules.mae), AmbientPalette.SlateLight)
                    Spacer(Modifier.height(4.dp))
                    MetricBar("calibrated", v.people.mae / max,
                        "%.2f".format(Locale.US, v.people.mae), AmbientPalette.PeopleColor)
                    if (v.improvementPct > 1.0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Calibration has cut the error by %.0f%% so far."
                                .format(Locale.US, v.improvementPct),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ----------------------------------------------------------- trend
            if (v.earlyLabels != null && v.recentLabels != null) {
                AmbientCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("Is it still improving?")
                        Spacer(Modifier.height(8.dp))
                        val max = maxOf(v.earlyLabels!!.mae, v.recentLabels!!.mae, 0.1)
                        MetricBar("first half of labels", v.earlyLabels!!.mae / max,
                            "%.2f".format(Locale.US, v.earlyLabels!!.mae), AmbientPalette.SlateLight)
                        Spacer(Modifier.height(4.dp))
                        MetricBar("most recent labels", v.recentLabels!!.mae / max,
                            "%.2f".format(Locale.US, v.recentLabels!!.mae), AmbientPalette.Olive)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (v.recentLabels!!.mae <= v.earlyLabels!!.mae + 0.05)
                                "Yes — the recent error is at or below where it started."
                            else
                                "Not yet. Later labels are usually taken in busier or different " +
                                    "conditions; keep going and the model keeps adapting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------------------------------------------------- scatter
            if (v.points.size >= 3) {
                AmbientCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("Predicted vs counted")
                        Spacer(Modifier.height(10.dp))
                        CalibrationScatter(v.points, Modifier.fillMaxWidth().height(190.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Each dot is one label. On the diagonal the app agrees with you; " +
                                "below it the app under-counts, above it over-counts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AmbientCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Vehicles")
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        BigStat("%.2f".format(Locale.US, v.vehicles.mae), "MAE vehicles", Modifier.weight(1f))
                        BigStat("%+.2f".format(Locale.US, v.vehicles.bias), "bias", Modifier.weight(1f))
                        BigStat("${summary.people.observations}", "labels used", Modifier.weight(1f))
                    }
                }
            }
        }

        // ------------------------------------------------------------- protocol
        AmbientCard(Modifier.fillMaxWidth(), tonal = true) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("How to collect a useful dataset")
                Spacer(Modifier.height(8.dp))
                ProtocolStep("1", "Pick one spot and stay there for 5-10 minutes with sensing running.")
                ProtocolStep("2", "Every ~30 s, count what you can genuinely see and submit it. " +
                    "Counts of 0 are just as valuable as counts of 8.")
                ProtocolStep("3", "Get at least 10 labels, ideally 25, spanning quiet and busy moments.")
                ProtocolStep("4", "Repeat in each place you care about — the model forgets on purpose " +
                    "so a new environment is learned quickly.")
                Spacer(Modifier.height(10.dp))
                Text(
                    "In simulation, ~25 labels in one place brought the error from ~4 people down to " +
                        "~0.7-1.8 depending on whether you then move somewhere new. Keep the phone " +
                        "in the same orientation and pocket as when you labelled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { /* handled by caller through the settings export */ },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Export labels as CSV from Settings")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier,
                    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        FieldLabel(label)
    }
}

@Composable
private fun MetricBar(label: String, fraction: Double, value: String,
                      color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        LevelBar(fraction.toFloat(), color, Modifier.weight(1f), height = 6.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
private fun ProtocolStep(number: String, text: String) {
    Row(Modifier.padding(vertical = 5.dp)) {
        Box(
            Modifier
                .size(20.dp)
                .padding(top = 2.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CalibrationScatter(points: List<Pair<Float, Float>>, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.outline
    val dot = AmbientPalette.PeopleColor
    val line = AmbientPalette.SlateLight
    val max = remember(points) { maxOf(10f, points.maxOf { maxOf(it.first, it.second) } * 1.2f) }
    Canvas(modifier = modifier) {
        val pad = 18.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        // axes
        drawLine(axis, Offset(pad, pad), Offset(pad, size.height - pad), strokeWidth = 1.5f)
        drawLine(axis, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad),
            strokeWidth = 1.5f)
        // ideal line y = x
        drawLine(
            line.copy(alpha = 0.6f),
            Offset(pad, size.height - pad),
            Offset(pad + w, size.height - pad - h),
            strokeWidth = 1.5f
        )
        points.forEach { (truth, est) ->
            val x = pad + (truth / max) * w
            val y = size.height - pad - (est / max) * h
            drawCircle(dot, radius = 6f, center = Offset(x.coerceIn(pad, size.width - pad),
                y.coerceIn(pad, size.height - pad)))
            drawCircle(
                dot.copy(alpha = 0.45f), radius = 6f,
                center = Offset(x.coerceIn(pad, size.width - pad), y.coerceIn(pad, size.height - pad)),
                style = Stroke(width = 3f)
            )
        }
        // legend box
        drawRect(
            color = line.copy(alpha = 0.15f),
            topLeft = Offset(size.width - pad - 54f, pad),
            size = Size(54f, 30f)
        )
    }
}

/**
 * The radius is the accuracy dial, so the accuracy tab is where you turn it: pick a
 * range, see what simulation predicts for it, and compare with what this phone has
 * actually managed here.
 */
@Composable
private fun RadiusExpectationCard(vm: AmbientViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val v by vm.validation.collectAsStateWithLifecycle()
    val model by vm.modelSummary.collectAsStateWithLifecycle()
    val r = settings.proximityRadiusM
    val expected = SenseRadius.expectedMae(r, model.peopleTrusted)

    AmbientCard(Modifier.fillMaxWidth(), tonal = true) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("Expected at ${r.roundToInt()} m")
            Spacer(Modifier.height(10.dp))
            RadiusChipRow(selectedMeters = r, onSelect = vm::setSensingRadius)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                BigStat(
                    "±%.1f".format(Locale.US, expected), "predicted", Modifier.weight(1f),
                    accent = AmbientPalette.SlateLight
                )
                BigStat(
                    if (v.labels >= 3) "±%.2f".format(Locale.US, v.people.mae) else "—",
                    "measured here", Modifier.weight(1f),
                    accent = AmbientPalette.PeopleColor
                )
                BigStat(
                    "${model.peopleTrusted}", "labels at this radius", Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Predicted comes from simulation — 10 five-minute scenes per radius — and " +
                    "is what you should expect while the calibration for this range is " +
                    "still thin. The measured column is this phone's own one-step-ahead " +
                    "error and takes over as soon as you have a handful of labels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
