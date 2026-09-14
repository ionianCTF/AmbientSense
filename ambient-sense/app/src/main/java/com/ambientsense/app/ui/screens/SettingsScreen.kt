package com.ambientsense.app.ui.screens

import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.data.HeatLayer
import com.ambientsense.app.data.SenseMode
import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.data.TileSource
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.AmbientCard
import com.ambientsense.app.ui.components.ChoiceChip
import com.ambientsense.app.ui.components.RadiusOptionList
import com.ambientsense.app.ui.components.SectionTitle
import com.ambientsense.app.ui.theme.AmbientPalette
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(vm: AmbientViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ------------------------------------------------------------- radios
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Radios")
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
                Text("BLE scan duty cycle", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BleModeOption("Low latency", ScanSettings.SCAN_MODE_LOW_LATENCY, settings, vm, Modifier.weight(1f))
                    BleModeOption("Balanced", ScanSettings.SCAN_MODE_BALANCED, settings, vm, Modifier.weight(1f))
                    BleModeOption("Low power", ScanSettings.SCAN_MODE_LOW_POWER, settings, vm, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Wi‑Fi scan interval",
                    value = settings.wifiScanIntervalMs / 1000f,
                    range = 10f..120f,
                    format = { "${it.toInt()} s" },
                    hint = "Android caps foreground apps at one scan per 30 s",
                    onValueChange = { vm.updateSettings { s -> s.copy(wifiScanIntervalMs = (it.toLong() * 1000L)) } }
                )
            }
        }

        // -------------------------------------------------------- rf geometry
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("RF geometry")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Path-loss exponent",
                    value = settings.pathLossExponent,
                    range = 1.5f..4.5f,
                    format = { String.format(Locale.US, "%.2f", it) },
                    hint = "2.0 free space · 2.6 indoors · 3.5 dense urban",
                    onValueChange = { vm.updateSettings { s -> s.copy(pathLossExponent = it) } }
                )
                SliderSetting(
                    label = "Fallback TX power at 1 m",
                    value = settings.defaultTxPower.toFloat(),
                    range = -85f..-35f,
                    format = { "${it.toInt()} dBm" },
                    hint = "Only used when a beacon does not advertise its own TX power",
                    onValueChange = { vm.updateSettings { s -> s.copy(defaultTxPower = it.toInt()) } }
                )
                Text(
                    "Everything below answers the question \"how much is within this " +
                        "radius?\". Widening it does not make the radios see further — it " +
                        "widens the question.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // -------------------------------------------------------- sensing radius
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Sensing radius")
                Spacer(Modifier.height(6.dp))
                Text(
                    "How far out the app reports on. Each row carries the accuracy " +
                        "measured for it before you calibrate and after about 25 labels at " +
                        "that radius.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                RadiusOptionList(
                    selectedMeters = settings.proximityRadiusM,
                    onSelect = vm::setSensingRadius
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Changing the radius invalidates the calibration. A model learned " +
                        "at one radius is measurably worse than no model at another, so the " +
                        "app falls back to its physics baseline here and re-earns trust as " +
                        "you label again. Your labels are never deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ------------------------------------------------------- classification
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Motion classification")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Standing below",
                    value = settings.staticSpeedMps,
                    range = 0.1f..1.5f,
                    format = { String.format(Locale.US, "%.2f m/s", it) },
                    onValueChange = { vm.updateSettings { s -> s.copy(staticSpeedMps = it) } }
                )
                SliderSetting(
                    label = "Walking up to",
                    value = settings.pedestrianMaxMps,
                    range = 0.5f..6f,
                    format = { String.format(Locale.US, "%.2f m/s", it) },
                    hint = "1.4 m/s is a typical walking pace",
                    onValueChange = { vm.updateSettings { s -> s.copy(pedestrianMaxMps = it) } }
                )
                SliderSetting(
                    label = "Vehicle above",
                    value = settings.vehicleMinMps,
                    range = 1f..15f,
                    format = { String.format(Locale.US, "%.1f m/s (%.0f km/h)", it, it * 3.6f) },
                    onValueChange = { vm.updateSettings { s -> s.copy(vehicleMinMps = it) } }
                )
                SliderSetting(
                    label = "Samples before classifying",
                    value = settings.minTrackSamples.toFloat(),
                    range = 2f..14f,
                    format = { "${it.toInt()}" },
                    onValueChange = { vm.updateSettings { s -> s.copy(minTrackSamples = it.toInt()) } }
                )
                SwitchSetting(
                    label = "Cluster devices that travel together",
                    checked = settings.groupDevices,
                    hint = "A car broadcasts a phone, a watch and a hands-free kit — count it once",
                    onCheckedChange = { vm.updateSettings { s -> s.copy(groupDevices = it) } }
                )
                SwitchSetting(
                    label = "Pass model + BIC model selection",
                    checked = settings.useBic,
                    hint = "Fit the hyperbolic range curve of something driving past, and only " +
                        "accept it if it beats a constant-range model",
                    onCheckedChange = { vm.updateSettings { s -> s.copy(useBic = it) } }
                )
                SwitchSetting(
                    label = "Early exit for fast, clean fits",
                    checked = settings.adaptiveGate,
                    hint = "A car is inside BLE range for about 3 seconds",
                    onCheckedChange = { vm.updateSettings { s -> s.copy(adaptiveGate = it) } }
                )
                SliderSetting(
                    label = "Speed significance",
                    value = settings.speedSigmaK,
                    range = 0f..4f,
                    format = { String.format(Locale.US, "%.1f σ", it) },
                    hint = "A speed must exceed this many standard errors before it counts as motion",
                    onValueChange = { vm.updateSettings { s -> s.copy(speedSigmaK = it) } }
                )
                SliderSetting(
                    label = "Fit window",
                    value = settings.fitWindowSec,
                    range = 4f..20f,
                    format = { "${it.toInt()} s" },
                    onValueChange = { vm.updateSettings { s -> s.copy(fitWindowSec = it) } }
                )
                SliderSetting(
                    label = "Samples before classifying",
                    value = settings.minTrackSamples.toFloat(),
                    range = 2f..10f,
                    format = { "${it.toInt()}" },
                    onValueChange = { vm.updateSettings { s -> s.copy(minTrackSamples = it.toInt()) } }
                )
            }
        }

        // ------------------------------------------------------- infrastructure
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Static devices")
                Spacer(Modifier.height(10.dp))
                SwitchSetting(
                    label = "Filter out building infrastructure",
                    checked = settings.excludeInfrastructure,
                    hint = "Laptops, TVs and beacons that never move are not people. This was the " +
                        "single biggest accuracy win in testing",
                    onCheckedChange = { vm.updateSettings { s -> s.copy(excludeInfrastructure = it) } }
                )
                SliderSetting(
                    label = "Dwell before calling it static",
                    value = settings.infrastructureDwellSec,
                    range = 20f..300f,
                    format = { "${it.toInt()} s" },
                    onValueChange = { vm.updateSettings { s -> s.copy(infrastructureDwellSec = it) } }
                )
                SliderSetting(
                    label = "Stillness threshold",
                    value = settings.infrastructureRangeStdM,
                    range = 0.1f..2f,
                    format = { String.format(Locale.US, "%.2f m", it) },
                    hint = "Range must vary by less than this — a standing person sways more",
                    onValueChange = { vm.updateSettings { s -> s.copy(infrastructureRangeStdM = it) } }
                )
                SliderSetting(
                    label = "Weight of a standing entity",
                    value = settings.staticEntityWeight,
                    range = 0f..1f,
                    format = { String.format(Locale.US, "%.2f", it) },
                    hint = "Motionless clusters are usually parked cars or laptops",
                    onValueChange = { vm.updateSettings { s -> s.copy(staticEntityWeight = it) } }
                )
            }
        }

        // ------------------------------------------------------------ heuristics
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Crowd heuristics")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Devices → people",
                    value = settings.deviceToPersonFactor,
                    range = 0.5f..3f,
                    format = { String.format(Locale.US, "%.2f×", it) },
                    hint = "Not everyone carries a discoverable device",
                    onValueChange = { vm.updateSettings { s -> s.copy(deviceToPersonFactor = it) } }
                )
                SliderSetting(
                    label = "Wi‑Fi fluctuation weight",
                    value = settings.rfPersonCoeff,
                    range = 0f..15f,
                    format = { String.format(Locale.US, "%.1f", it) },
                    onValueChange = { vm.updateSettings { s -> s.copy(rfPersonCoeff = it) } }
                )
                SliderSetting(
                    label = "Blocking-event weight",
                    value = settings.dipPersonCoeff,
                    range = 0f..2f,
                    format = { String.format(Locale.US, "%.2f", it) },
                    onValueChange = { vm.updateSettings { s -> s.copy(dipPersonCoeff = it) } }
                )
                SliderSetting(
                    label = "Vehicle weight on fast dips",
                    value = settings.vehicleDipCoeff,
                    range = 0f..1f,
                    format = { String.format(Locale.US, "%.2f", it) },
                    onValueChange = { vm.updateSettings { s -> s.copy(vehicleDipCoeff = it) } }
                )
            }
        }

        // -------------------------------------------------------------- acoustics
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Acoustics")
                Spacer(Modifier.height(10.dp))
                SwitchSetting(
                    label = "Measure ambient noise",
                    checked = settings.audioEnabled,
                    hint = "A-weighted Leq, captured while the app is on screen",
                    onCheckedChange = { vm.updateSettings { s -> s.copy(audioEnabled = it) } }
                )
                SliderSetting(
                    label = "Microphone calibration",
                    value = settings.micCalibrationDb,
                    range = 60f..120f,
                    format = { "${it.toInt()} dB" },
                    hint = "Trim against a reference meter until the reading matches",
                    onValueChange = { vm.updateSettings { s -> s.copy(micCalibrationDb = it) } }
                )
            }
        }

        // ---------------------------------------------------------- connectivity
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Connectivity probes")
                Spacer(Modifier.height(10.dp))
                TextSetting(
                    label = "Ping target",
                    value = settings.pingHost,
                    onValueChange = { vm.updateSettings { s -> s.copy(pingHost = it) } }
                )
                SliderSetting(
                    label = "Packets per probe",
                    value = settings.pingCount.toFloat(),
                    range = 3f..10f,
                    format = { "${it.toInt()}" },
                    onValueChange = { vm.updateSettings { s -> s.copy(pingCount = it.toInt()) } }
                )
                SliderSetting(
                    label = "Ping interval",
                    value = settings.pingIntervalMs / 1000f,
                    range = 5f..120f,
                    format = { "${it.toInt()} s" },
                    onValueChange = { vm.updateSettings { s -> s.copy(pingIntervalMs = it.toLong() * 1000L) } }
                )
                TextSetting(
                    label = "Download test URL",
                    value = settings.speedTestUrl,
                    onValueChange = { vm.updateSettings { s -> s.copy(speedTestUrl = it) } }
                )
                SliderSetting(
                    label = "Speed test interval",
                    value = settings.speedTestIntervalMs / 1000f,
                    range = 60f..900f,
                    format = { "${it.toInt()} s" },
                    hint = "Each test downloads up to 10 MB — mind your data plan",
                    onValueChange = { vm.updateSettings { s -> s.copy(speedTestIntervalMs = it.toLong() * 1000L) } }
                )
                SwitchSetting(
                    label = "Run upload test too",
                    checked = settings.runUploadTest,
                    onCheckedChange = { vm.updateSettings { s -> s.copy(runUploadTest = it) } }
                )
            }
        }

        // ------------------------------------------------------------------- ui
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Map & display")
                Spacer(Modifier.height(10.dp))
                Text("Tile source", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileSource.entries.forEach { src ->
                        ChoiceChip(
                            text = src.label.split(" ").first(),
                            selected = settings.mapTileSource == src,
                            onClick = { vm.updateSettings { it.copy(mapTileSource = src) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Default map layer", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeatLayer.entries.forEach { layer ->
                        ChoiceChip(
                            text = layer.label,
                            selected = settings.heatLayer == layer,
                            onClick = { vm.updateSettings { it.copy(heatLayer = layer) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SwitchSetting(
                    label = "Keep screen on while sensing",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { vm.updateSettings { s -> s.copy(keepScreenOn = it) } }
                )
                SwitchSetting(
                    label = "Show unclassified devices",
                    checked = settings.showUnclassified,
                    onCheckedChange = { vm.updateSettings { s -> s.copy(showUnclassified = it) } }
                )
            }
        }

        // -------------------------------------------------------------- storage
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Sampling & data")
                Spacer(Modifier.height(10.dp))
                SliderSetting(
                    label = "Fusion tick",
                    value = settings.fusionIntervalMs / 1000f,
                    range = 0.5f..5f,
                    format = { String.format(Locale.US, "%.1f s", it) },
                    onValueChange = { vm.updateSettings { s -> s.copy(fusionIntervalMs = (it * 1000).toLong()) } }
                )
                SliderSetting(
                    label = "Write sample every",
                    value = settings.persistIntervalMs / 1000f,
                    range = 5f..120f,
                    format = { "${it.toInt()} s" },
                    onValueChange = { vm.updateSettings { s -> s.copy(persistIntervalMs = it.toLong() * 1000L) } }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                vm.exportSamplesCsv()?.let { shareCsv(context, it) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Export samples") }
                    Button(
                        onClick = {
                            scope.launch {
                                vm.exportLabelsCsv()?.let { shareCsv(context, it) }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Export labels") }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { scope.launch { vm.clearHistory() } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Clear history") }
                    Button(
                        onClick = { vm.clearLabels() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) { Text("Clear labels") }
                }
            }
        }

        // ---------------------------------------------------------------- notes
        AmbientCard(Modifier.fillMaxWidth(), tonal = true) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("How it works")
                Spacer(Modifier.height(8.dp))
                MethodNote(
                    title = "Device-free Wi‑Fi sensing",
                    body = "Bodies and vehicles disturb the RF field between you and every access point " +
                        "you can hear. Ambient Sense tracks the fluctuation of received power and the rate " +
                        "of shadowing dips per link. The inter-event statistics of those dips are what " +
                        "Depatla & Mostofi showed to be robust for counting crowds through walls " +
                        "(SECON 2017/2018, IEEE TMC 2019)."
                )
                MethodNote(
                    title = "Device-based BLE tracking",
                    body = "Each advertisement is converted to a range with the log-distance path-loss " +
                        "model, and the range is regressed against time to obtain a radial velocity. " +
                        "Slow = standing, ~1.4 m/s = walking, fast = vehicle. Devices whose range curves " +
                        "move together are clustered so a car with a phone, watch and hands-free kit " +
                        "counts once."
                )
                MethodNote(
                    title = "Your labels realign it",
                    body = "Every ground-truth count you submit trains a recursive least squares model — " +
                        "with a forgetting factor, so recent corrections matter more than old ones. It " +
                        "learns the correction to the physics baseline rather than the count itself, so a " +
                        "few labels already help and a bad one cannot throw the estimate far off."
                )
                MethodNote(
                    title = "Known limits",
                    body = "A single phone measures range, not bearing; tangential motion near the point " +
                        "of closest approach looks slow. Randomised MAC addresses rotate every ~15 min " +
                        "and inflate naive device counts. Absolute accuracy is best when you calibrate in " +
                        "each environment."
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Back to the field") }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------ primitives

@Composable
private fun BleModeOption(
    label: String,
    mode: Int,
    settings: SenseSettings,
    vm: AmbientViewModel,
    modifier: Modifier = Modifier
) {
    ChoiceChip(
        text = label,
        selected = settings.bleScanMode == mode,
        onClick = { vm.updateSettings { it.copy(bleScanMode = mode) } },
        modifier = modifier
    )
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
    hint: String? = null
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = format(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(96.dp),
                textAlign = TextAlign.End
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun TextSetting(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun MethodNote(title: String, body: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(3.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shareCsv(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Export measurements")) }
}
