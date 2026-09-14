package com.ambientsense.app.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.sensing.NetworkProbe.Companion.rsrpQuality
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.AmbientCard
import com.ambientsense.app.ui.components.KeyValueRow
import com.ambientsense.app.ui.components.LevelBar
import com.ambientsense.app.ui.components.SectionTitle
import com.ambientsense.app.ui.components.Sparkline
import com.ambientsense.app.ui.theme.AmbientPalette
import com.ambientsense.app.util.formatAgo
import com.ambientsense.app.util.formatMbps
import com.ambientsense.app.util.formatMs
import java.util.Locale

/**
 * Connectivity report: what the radio link is, how good it is, and what it delivers
 * right now (ICMP latency + jitter + loss, TCP throughput).
 */
@Composable
fun SignalScreen(vm: AmbientViewModel) {
    val net by vm.net.collectAsStateWithLifecycle()
    val pingHistory by vm.pingHistory.collectAsStateWithLifecycle()
    val downHistory by vm.downHistory.collectAsStateWithLifecycle()
    val probing by vm.probeRunning.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        // ---------------------------------------------------------- transport
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Transport")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = net.transport,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = net.networkType,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Operator", net.cell.operator ?: "—")
                KeyValueRow("Generation", "${net.networkType} · ${net.cell.detailedTech}")
                KeyValueRow("Metered", if (net.isMetered) "Yes" else "No")
                net.validated?.let {
                    KeyValueRow("Internet validated", if (it) "Yes" else "No")
                }
                listOfNotNull(
                    net.linkDownMbps?.let { "link down ${it} Mbps" },
                    net.linkUpMbps?.let { "link up ${it} Mbps" }
                ).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                    KeyValueRow("Reported link", it)
                }
            }
        }

        // ------------------------------------------------------------ cellular
        if (net.transport == "Cellular") {
            AmbientCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Cellular radio")
                    Spacer(Modifier.height(8.dp))
                    val rsrp = net.cell.rsrpDbm
                    KeyValueRow("RSRP", rsrp?.let { "$it dBm · ${rsrpQuality(it)}" } ?: "—")
                    rsrp?.let {
                        Spacer(Modifier.height(6.dp))
                        LevelBar(
                            fraction = ((it + 140) / 70f).coerceIn(0f, 1f),
                            color = AmbientPalette.SignalRamp[
                                (((it + 140) / 70f).coerceIn(0f, 0.99f) * 5).toInt()
                            ],
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    KeyValueRow("RSRQ", net.cell.rsrqDb?.let { "$it dB" } ?: "—")
                    KeyValueRow("SINR", net.cell.sinrDb?.let { "$it dB" } ?: "—")
                    KeyValueRow("Band", net.cell.band ?: "—")
                    KeyValueRow("Cell", net.cell.cellId ?: "—")
                    KeyValueRow("Timing advance", net.cell.timingAdvance?.toString() ?: "—")
                    KeyValueRow("Neighbour cells", "${net.cell.neighborCells}")
                }
            }
        }

        // --------------------------------------------------------------- wifi
        if (net.transport == "Wi‑Fi" || net.wifiSsid != null) {
            AmbientCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle("Wi‑Fi link")
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("SSID", net.wifiSsid ?: "hidden")
                    KeyValueRow("Standard", net.wifiStandard ?: "—")
                    KeyValueRow("Frequency", net.wifiFreqMhz?.let { "$it MHz" } ?: "—")
                    KeyValueRow("Link rate", net.wifiLinkMbps?.let { "$it Mbps" } ?: "—")
                    KeyValueRow("RSSI", net.wifiRssiDbm?.let { "$it dBm" } ?: "—")
                }
            }
        }

        // ------------------------------------------------------------- latency
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Latency to ${net.ping.host.ifBlank { settings.pingHost }}")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    LatencyCell("min", net.ping.minMs, Modifier.weight(1f))
                    LatencyCell("avg", net.ping.avgMs, Modifier.weight(1f))
                    LatencyCell("max", net.ping.maxMs, Modifier.weight(1f))
                    LatencyCell("jitter", net.ping.jitterMs, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                if (pingHistory.size > 1) {
                    Sparkline(
                        values = pingHistory,
                        color = AmbientPalette.NetworkColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                val via = if (net.ping.viaTcp) "TCP handshake fallback" else "ICMP"
                KeyValueRow("Loss", formatMs(net.ping.lossPct)?.let { "$it %" } ?: "—")
                KeyValueRow("Packets", "${net.ping.packetsReceived}/${net.ping.packetsSent}")
                KeyValueRow("Method", via)
                KeyValueRow("Measured", net.ping.atMs.takeIf { it > 0 }?.let { formatAgo(now, it) } ?: "—")
            }
        }

        // ---------------------------------------------------------- throughput
        AmbientCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Throughput")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = formatMbps(net.download.mbps),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("download Mbps", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = formatMbps(net.upload.mbps),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("upload Mbps", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (downHistory.size > 1) {
                    Sparkline(
                        values = downHistory,
                        color = AmbientPalette.StoneDeep,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                net.download.error?.let {
                    Text(
                        "Download: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                net.upload.error?.let {
                    Text(
                        "Upload: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                KeyValueRow(
                    "Transferred",
                    String.format(
                        Locale.US, "%.1f MB in %d ms",
                        net.download.bytes / 1_000_000.0, net.download.durationMs
                    )
                )
                KeyValueRow(
                    "Last test",
                    net.download.atMs.takeIf { it > 0 }?.let { formatAgo(now, it) } ?: "—"
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.runProbeNow() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    enabled = !probing,
                    shape = MaterialTheme.shapes.small
                ) {
                    if (probing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Probing…")
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ping + speed test now")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LatencyCell(label: String, value: Float?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = formatMs(value),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
