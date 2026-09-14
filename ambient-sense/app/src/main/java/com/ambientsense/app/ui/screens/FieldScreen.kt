package com.ambientsense.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.data.SenseMode
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.AmbientCard
import com.ambientsense.app.ui.components.ChoiceChip
import com.ambientsense.app.ui.components.ConfidencePill
import com.ambientsense.app.ui.components.Dot
import com.ambientsense.app.ui.components.FieldLabel
import com.ambientsense.app.ui.components.MetricTile
import com.ambientsense.app.ui.components.RadiusAccuracyLine
import com.ambientsense.app.ui.components.RadiusChipRow
import com.ambientsense.app.ui.components.RadialGauge
import com.ambientsense.app.ui.components.SectionTitle
import com.ambientsense.app.ui.components.Sparkline
import com.ambientsense.app.ui.theme.AmbientPalette
import com.ambientsense.app.util.formatCount
import com.ambientsense.app.util.formatDb
import com.ambientsense.app.util.formatMbps
import com.ambientsense.app.util.formatMs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The main dashboard: what the radio environment says is around you right now,
 * plus the acoustic and connectivity readouts, all on one page.
 */
@Composable
fun FieldScreen(vm: AmbientViewModel) {
    val running by vm.running.collectAsStateWithLifecycle()
    val fusion by vm.fusion.collectAsStateWithLifecycle()
    val wifi by vm.wifi.collectAsStateWithLifecycle()
    val ble by vm.ble.collectAsStateWithLifecycle()
    val noise by vm.noise.collectAsStateWithLifecycle()
    val net by vm.net.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val model by vm.modelSummary.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------------------------------------------------------- headline
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FieldLabel("Around you")
                    ConfidencePill(
                        confidence = fusion.confidence,
                        text = "${(fusion.confidence * 100).toInt()}% confidence"
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GaugeBlock(
                        value = fusion.people,
                        label = "people",
                        color = AmbientPalette.PeopleColor,
                        confidence = fusion.confidence
                    )
                    GaugeBlock(
                        value = fusion.vehicles,
                        label = "vehicles",
                        color = AmbientPalette.VehicleColor,
                        confidence = fusion.confidence
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = fusion.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ------------------------------------------------------ sensing radius
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Sensing radius", trailing = {
                    Text(
                        text = "${settings.proximityRadiusM.roundToInt()} m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                })
                Spacer(Modifier.height(10.dp))
                RadiusChipRow(
                    selectedMeters = settings.proximityRadiusM,
                    onSelect = vm::setSensingRadius
                )
                Spacer(Modifier.height(12.dp))
                RadiusAccuracyLine(
                    meters = settings.proximityRadiusM,
                    labelsAtRadius = model.peopleTrusted,
                    detailed = true
                )
            }
        }

        // ------------------------------------------------------ radio selector
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Sensing mode")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SenseMode.entries.forEach { mode ->
                        ChoiceChip(
                            text = mode.label,
                            selected = settings.mode == mode,
                            onClick = { vm.updateSettings { it.copy(mode = mode) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { if (running) vm.stopSensing() else vm.startSensing() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (running) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            contentColor = if (running) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (running) "Stop sensing" else "Start sensing")
                    }
                }
                if (running) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiniStat(
                            "Wi‑Fi", "${wifi.apCount} APs",
                            wifi.cvMedian.toString().take(5) + " cv",
                            AmbientPalette.Kraft,
                            Modifier.weight(1f)
                        )
                        MiniStat(
                            "BLE", "${ble.entityCount} entities",
                            "${ble.totalTracked} devices",
                            AmbientPalette.PeopleColor,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------- tiles
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Noise",
                value = formatDb(noise.leqDba),
                unit = "dB(A)",
                accent = AmbientPalette.NoiseColor,
                hint = noise.floorDba?.let { "floor ${formatDb(it)} dB" } ?: "peak ${formatDb(noise.peakDba)} dB",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "RF turbulence",
                value = String.format("%.2f", wifi.activityIndex),
                unit = "idx",
                accent = AmbientPalette.Kraft,
                hint = "${wifi.linksTracked} links · ${String.format("%.1f", wifi.dipRatePerMin)} dips/min",
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Walking",
                value = "${ble.pedestrianCount}",
                unit = "entities",
                accent = AmbientPalette.PeopleColor,
                hint = "${ble.pedestriansPassed} passed this session",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Standing",
                value = "${ble.staticCount}",
                unit = "entities",
                accent = AmbientPalette.StaticColor,
                hint = "within ${settings.proximityRadiusM.toInt()} m",
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Latency",
                value = formatMs(net.ping.avgMs),
                unit = "ms",
                accent = AmbientPalette.NetworkColor,
                hint = "jitter ${formatMs(net.ping.jitterMs)} · loss ${formatMs(net.ping.lossPct)}%",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Download",
                value = formatMbps(net.download.mbps),
                unit = "Mbps",
                accent = AmbientPalette.StoneDeep,
                hint = net.networkType,
                modifier = Modifier.weight(1f)
            )
        }

        // ------------------------------------------------------------ history
        if (samples.size > 3) {
            AmbientCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Session trace")
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "people",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmbientPalette.PeopleColor,
                            modifier = Modifier.width(64.dp)
                        )
                        Sparkline(
                            values = samples.map { it.people },
                            color = AmbientPalette.PeopleColor,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "vehicles",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmbientPalette.VehicleColor,
                            modifier = Modifier.width(64.dp)
                        )
                        Sparkline(
                            values = samples.map { it.vehicles },
                            color = AmbientPalette.VehicleColor,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        )
                    }
                    noise.leqDba?.let {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "noise",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmbientPalette.NoiseColor,
                                modifier = Modifier.width(64.dp)
                            )
                            Sparkline(
                                values = samples.mapNotNull { it.noiseDba },
                                color = AmbientPalette.NoiseColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GaugeBlock(
    value: Float,
    label: String,
    color: Color,
    confidence: Float
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
            RadialGauge(
                value = value,
                max = maxOf(8f, ceil(value * 1.6f)),
                label = label,
                color = color,
                confidence = confidence,
                modifier = Modifier.fillMaxSize()
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatCount(value),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    hint: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(color = color, size = 8.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
