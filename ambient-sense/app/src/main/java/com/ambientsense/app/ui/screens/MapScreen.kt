package com.ambientsense.app.ui.screens

import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.data.HeatLayer
import com.ambientsense.app.data.TileSource
import com.ambientsense.app.model.AmbientSample
import com.ambientsense.app.model.DeviceTrack
import com.ambientsense.app.model.GeoFix
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.components.Dot
import com.ambientsense.app.ui.theme.AmbientPalette
import com.ambientsense.app.ui.theme.motionColor
import com.ambientsense.app.util.formatMeters
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Everything the canvas overlay needs for one frame. */
private data class HeatFrame(
    val layer: HeatLayer,
    val samples: List<AmbientSample>,
    val tracks: List<DeviceTrack>,
    val fix: GeoFix?,
    val proximityM: Float
)

/**
 * Unified map: every measurement of the session is dropped where it was taken, and the
 * tracked BLE devices are drawn on their measured range ring around the phone.
 *
 * Honest limitation, surfaced in the UI: a single receiver measures *range*, not
 * *bearing*, so a device's angle here is a stable pseudo-bearing derived from its
 * address — enough to keep markers from piling up, not a claim about true direction.
 * Trilaterating real positions needs two or more observers.
 */
@Composable
fun MapScreen(vm: AmbientViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    val tracks by vm.ble.collectAsStateWithLifecycle()
    val fix by vm.location.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    val overlay = remember { AmbientOverlay(HeatFrame(HeatLayer.PEOPLE, emptyList(), emptyList(), null, 25f)) }
    val mapView = rememberMapViewWithLifecycle(context) { mv ->
        mv.overlays.add(overlay)
    }

    LaunchedEffect(settings.mapTileSource) {
        mapView.setTileSource(
            when (settings.mapTileSource) {
                TileSource.MAPNIK -> TileSourceFactory.MAPNIK
                TileSource.HIKING -> XYTileSource(
                    "OpenTopoMap", 0, 17, 256, ".png",
                    arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/")
                )
                TileSource.CYCLING -> XYTileSource(
                    "CyclOSM", 0, 19, 256, ".png",
                    arrayOf("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/")
                )
                TileSource.DARK -> XYTileSource(
                    "CARTO Dark", 0, 20, 256, ".png",
                    arrayOf("https://a.basemaps.cartocdn.com/dark_all/")
                )
            }
        )
        mapView.invalidate()
    }

    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(fix) {
        val f = fix ?: return@LaunchedEffect
        if (!centered || !running) {
            mapView.controller.setCenter(GeoPoint(f.lat, f.lon))
            mapView.controller.setZoom(17.5)
            centered = true
        }
    }

    val frame = HeatFrame(settings.heatLayer, samples, tracks.tracks, fix, settings.proximityRadiusM)
    LaunchedEffect(frame) {
        overlay.frame = frame
        mapView.invalidate()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // ------------------------------------------------------- layer chooser
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                HeatLayer.entries.forEach { layer ->
                    val selected = layer == settings.heatLayer
                    Surface(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(percent = 50)
                            ),
                        shape = RoundedCornerShape(percent = 50),
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        onClick = { vm.updateSettings { it.copy(heatLayer = layer) } }
                    ) {
                        Text(
                            text = layer.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------- legend
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
        ) {
            LegendCard(
                layer = settings.heatLayer,
                sampleCount = samples.count { it.lat != 0.0 || it.lon != 0.0 },
                deviceCount = tracks.tracks.size,
                proximityM = settings.proximityRadiusM,
                accuracyM = fix?.accuracyM
            )
        }

        FloatingActionButton(
            onClick = {
                fix?.let { mapView.controller.animateTo(GeoPoint(it.lat, it.lon)) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
                .size(48.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Outlined.MyLocation, contentDescription = "Recenter")
        }
    }
}

@Composable
private fun LegendCard(
    layer: HeatLayer,
    sampleCount: Int,
    deviceCount: Int,
    proximityM: Float,
    accuracyM: Float?
) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color = layerColor(layer), size = 9.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "${layer.label} heat · $sampleCount points",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "$deviceCount devices on range rings",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Proximity ring ${proximityM.toInt()} m${accuracyM?.let { " · GPS ±${it.toInt()} m" } ?: ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Range only — bearing is a stable pseudo-angle, not a fix",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun layerColor(layer: HeatLayer): Color = when (layer) {
    HeatLayer.PEOPLE -> AmbientPalette.PeopleColor
    HeatLayer.VEHICLES -> AmbientPalette.VehicleColor
    HeatLayer.NOISE -> AmbientPalette.NoiseColor
    HeatLayer.THROUGHPUT -> AmbientPalette.StoneDeep
    HeatLayer.PING -> AmbientPalette.Kraft
}

private fun layerRange(layer: HeatLayer): ClosedFloatingPointRange<Float> = when (layer) {
    HeatLayer.PEOPLE -> 0f..15f
    HeatLayer.VEHICLES -> 0f..8f
    HeatLayer.NOISE -> 35f..95f
    HeatLayer.THROUGHPUT -> 0f..100f
    HeatLayer.PING -> 0f..150f
}

private fun sampleValue(layer: HeatLayer, s: AmbientSample): Float? = when (layer) {
    HeatLayer.PEOPLE -> s.people
    HeatLayer.VEHICLES -> s.vehicles
    HeatLayer.NOISE -> s.noiseDba
    HeatLayer.THROUGHPUT -> s.downMbps
    HeatLayer.PING -> s.pingMs
}

// ---------------------------------------------------------------- canvas overlay

private class AmbientOverlay(initial: HeatFrame) : Overlay() {

    @Volatile var frame: HeatFrame = initial

    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = 0x556A8CAF
    }
    private val proxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.4f
        color = 0x88CC785C.toInt()
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = 0x66191919
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F6FB0.toInt() }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x332F6FB0 }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val scratch = Point()

    override fun draw(c: AndroidCanvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val f = frame
        val proj: Projection = osmv.projection
        val fix = f.fix ?: return

        val me = GeoPoint(fix.lat, fix.lon)
        proj.toPixels(me, scratch)
        val mx = scratch.x
        val my = scratch.y

        // pixels per metre, derived empirically so it survives zoom changes
        val north = me.destinationPoint(100.0, 0.0)
        val p2 = Point()
        proj.toPixels(north, p2)
        val pxPerMeter = (hypot((p2.x - mx).toDouble(), (p2.y - my).toDouble()) / 100.0)
            .coerceAtLeast(1e-6)

        // ---- route trail
        val pts = f.samples.filter { it.lat != 0.0 || it.lon != 0.0 }
        if (pts.size > 1) {
            var prev: Point? = null
            for (s in pts) {
                proj.toPixels(GeoPoint(s.lat, s.lon), scratch)
                val cur = Point(scratch.x, scratch.y)
                prev?.let { c.drawLine(it.x.toFloat(), it.y.toFloat(), cur.x.toFloat(), cur.y.toFloat(), routePaint) }
                prev = cur
            }
        }

        // ---- measurement heat
        val range = layerRange(f.layer)
        val color = layerColor(f.layer)
        val androidColor = android.graphics.Color.argb(
            255,
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
        for (s in pts) {
            val v = sampleValue(f.layer, s) ?: continue
            val norm = ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            if (norm <= 0f) continue
            proj.toPixels(GeoPoint(s.lat, s.lon), scratch)
            blobPaint.color = androidColor
            blobPaint.alpha = (26 + 92 * norm).toInt().coerceIn(0, 255)
            c.drawCircle(
                scratch.x.toFloat(), scratch.y.toFloat(),
                (16f + 44f * norm) * (pxPerMeter * 1.0f).toFloat().coerceAtMost(2.2f).coerceAtLeast(0.6f),
                blobPaint
            )
        }

        // ---- range rings + device markers
        for (metres in intArrayOf(10, 25, 50, 100)) {
            val r = (metres * pxPerMeter).toFloat()
            if (r < 8f || r > 4000f) continue
            c.drawCircle(mx.toFloat(), my.toFloat(), r, ringPaint)
        }
        val proxR = (f.proximityM * pxPerMeter).toFloat()
        if (proxR > 6f) c.drawCircle(mx.toFloat(), my.toFloat(), proxR, proxPaint)

        for (t in f.tracks) {
            val bearing = pseudoBearing(t.key)
            val r = (t.distanceM * pxPerMeter).toFloat().coerceAtMost(4000f)
            val rad = Math.toRadians(bearing)
            val x = mx + (r * sin(rad)).toFloat()
            val y = my - (r * cos(rad)).toFloat()
            val mc = motionColor(t.motion)
            dotPaint.color = android.graphics.Color.argb(
                235, (mc.red * 255).toInt(), (mc.green * 255).toInt(), (mc.blue * 255).toInt()
            )
            c.drawCircle(x, y, 9f, dotPaint)
            dotPaint.color = 0x33FFFFFF
            c.drawCircle(x, y, 13f, dotPaint)
            if (t.distanceM <= f.proximityM) {
                c.drawText(t.distanceM.toInt().toString(), x, y + 6f, textPaint)
            }
        }

        // ---- observer
        c.drawCircle(mx.toFloat(), my.toFloat(), (fix.accuracyM * pxPerMeter).toFloat().coerceAtLeast(10f), haloPaint)
        c.drawCircle(mx.toFloat(), my.toFloat(), 8f, mePaint)
    }

    /** Deterministic 0..359 pseudo-bearing so markers do not jump around. */
    private fun pseudoBearing(key: String): Double {
        var h = 7
        for (ch in key) h = (h * 31 + ch.code) and 0x7fffffff
        return (h % 360).toDouble()
    }
}

// -------------------------------------------------------------- lifecycle glue

@Composable
fun rememberMapViewWithLifecycle(
    context: android.content.Context,
    configure: (MapView) -> Unit = {}
): MapView {
    val mapView = remember {
        MapView(context.applicationContext).apply {
            isTilesScaledToDpi = true
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(17.0)
            configure(this)
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { mapView.onDetach() }
        }
    }
    return mapView
}
