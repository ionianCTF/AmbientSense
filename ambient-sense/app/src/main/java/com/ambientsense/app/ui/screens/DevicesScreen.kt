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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.model.DeviceTrack
import com.ambientsense.app.model.MotionClass
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.AmbientCard
import com.ambientsense.app.ui.components.Dot
import com.ambientsense.app.ui.components.FieldLabel
import com.ambientsense.app.ui.components.SectionTitle
import com.ambientsense.app.ui.components.Sparkline
import com.ambientsense.app.ui.theme.AmbientPalette
import com.ambientsense.app.ui.theme.motionColor
import com.ambientsense.app.util.formatAgo
import com.ambientsense.app.util.formatMeters
import java.util.Locale

private enum class DeviceFilter(val label: String) {
    ALL("All"), VEHICLE("Vehicles"), WALKING("Walking"), STANDING("Standing")
}

/**
 * Everything the BLE tracker currently sees, with the range-rate fit that decides
 * whether a device is a parked phone, a person on foot, or something in a car.
 */
@Composable
fun DevicesScreen(vm: AmbientViewModel) {
    val ble by vm.ble.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(DeviceFilter.ALL) }
    val now = System.currentTimeMillis()

    val filtered = ble.tracks.filter {
        when (filter) {
            DeviceFilter.ALL -> settings.showUnclassified || it.motion != MotionClass.UNKNOWN
            DeviceFilter.VEHICLE -> it.motion == MotionClass.VEHICLE
            DeviceFilter.WALKING -> it.motion == MotionClass.PEDESTRIAN
            DeviceFilter.STANDING -> it.motion == MotionClass.STATIC
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClassCounter("Walking", ble.pedestrianCount, AmbientPalette.PeopleColor, Modifier.weight(1f))
                ClassCounter("Standing", ble.staticCount, AmbientPalette.StaticColor, Modifier.weight(1f))
                ClassCounter("Vehicles", ble.vehicleCount, AmbientPalette.VehicleColor, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeviceFilter.entries.forEach { f ->
                    val selected = f == filter
                    Surface(
                        modifier = Modifier.border(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(percent = 50)
                        ),
                        shape = RoundedCornerShape(percent = 50),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        onClick = { filter = f }
                    ) {
                        Text(
                            f.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            SectionTitle("Tracked now · ${ble.entityCount} entities from ${ble.totalTracked} devices")
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (running) "No devices match this filter yet" else "Start sensing to track BLE devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.key }) { track ->
                    DeviceCard(track, now, settings.proximityRadiusM)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun ClassCounter(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color, size = 7.dp)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(2.dp))
        Text("$count", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DeviceCard(track: DeviceTrack, now: Long, proximityM: Float) {
    val color = motionColor(track.motion)
    val kmh = track.radialSpeedMps * 3.6f
    AmbientCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color, size = 10.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append(track.key)
                            if (track.randomizedMac) append(" · randomised")
                            if (track.groupId >= 0) append(" · cluster #${track.groupId}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = track.motion.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color
                    )
                    Text(
                        text = formatAgo(now, track.lastSeenMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth()) {
                StatCell("Range", formatMeters(track.distanceM))
                StatCell(
                    "Range rate",
                    if (track.speedSigmaMps.isFinite())
                        String.format(Locale.US, "%+.2f ± %.2f m/s", track.radialSpeedMps, track.speedSigmaMps)
                    else String.format(Locale.US, "%+.2f m/s", track.radialSpeedMps)
                )
                StatCell("Speed", String.format(Locale.US, "%.1f km/h", kotlin.math.abs(kmh)))
                StatCell("Fit R²", String.format(Locale.US, "%.2f", track.fitR2))
            }

            if (track.history.size > 2) {
                Spacer(Modifier.height(10.dp))
                FieldLabel("Range over time")
                Spacer(Modifier.height(4.dp))
                Sparkline(
                    values = track.history,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.samples} samples · RSSI ${track.rssi} dBm" +
                        (if (track.txPower > -127) " · TX ${track.txPower} dBm" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                if (track.infrastructure) {
                    Box(
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(percent = 50)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "static device — not counted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                if (track.distanceM <= proximityM) {
                    Box(
                        Modifier
                            .background(color.copy(alpha = 0.15f), RoundedCornerShape(percent = 50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "in proximity",
                            style = MaterialTheme.typography.labelSmall,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(Modifier.padding(end = 14.dp)) {
        FieldLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
