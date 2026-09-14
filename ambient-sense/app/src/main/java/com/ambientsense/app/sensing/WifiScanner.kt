package com.ambientsense.app.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.model.WifiMetrics
import com.ambientsense.app.util.medianOf
import com.ambientsense.app.util.percentileOf
import com.ambientsense.app.util.dbmToLinear
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Device-free Wi-Fi sensing.
 *
 * We never connect to anything — we only read the passive scan table. Two physical
 * effects make the ether "turbulent" when people and vehicles move through it:
 *
 *  1. **Shadowing / blocking events.** A body crossing a link produces a characteristic
 *     dip in received power. Depatla & Mostofi (SECON 2018, IEEE TMC 2019) model the
 *     *inter-event times* of these dips and show they are far more robust to walls and
 *     absolute attenuation than the dip depth, which is what we exploit here.
 *  2. **Multipath fluctuation.** Even without a crossing, moving scatterers raise the
 *     variance of the received power. The coefficient of variation of *linear* power
 *     (not dB) is the statistic that tracks occupancy most cleanly.
 *
 * We therefore keep a short ring buffer of RSSI per BSSID and publish statistics over
 * the last few minutes: fluctuation level, dip rate, and AP churn.
 *
 * Platform caveat: since Android 9 a foreground app is limited to 4 scans per 2
 * minutes, so [SenseSettings.wifiScanIntervalMs] below ~30 s has no effect; the
 * scanner degrades gracefully and keeps whatever cadence the OS allows.
 */
class WifiScanner(
    private val context: Context,
    private val settings: () -> SenseSettings
) {

    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    /** Guards [links], [firstSeen] and [appearanceTimes] across the scan receiver
     *  (main thread) and the fusion tick (background dispatcher). */
    private val lock = Any()

    private val links = HashMap<String, LinkState>()
    private val firstSeen = HashMap<String, Long>()
    private val appearanceTimes = ArrayDeque<Long>()
    private var scanCount = 0
    private var lastScanMs = 0L
    private var receiver: BroadcastReceiver? = null

    var lastError: String? = null
        private set

    fun hasPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ||
                    intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
                ) {
                    harvest()
                }
            }
        }
        receiver = r
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        ContextCompat.registerReceiver(
            context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    @SuppressLint("MissingPermission")
    fun requestScan() {
        if (!hasPermission()) {
            lastError = "Wi‑Fi scan permission missing"
            return
        }
        if (wifiManager?.isWifiEnabled == false) {
            // Starting a scan re-enables Wi-Fi on most devices; on Android 10+ it needs
            // explicit user intent, so we only ask when the radio is already on.
            lastError = "Wi‑Fi radio is off"
            return
        }
        runCatching {
            @Suppress("DEPRECATION")
            val ok = wifiManager?.startScan() ?: false
            lastError = if (ok) null else "Scan throttled by the OS"
        }.onFailure { lastError = it.message }
    }

    @SuppressLint("MissingPermission")
    fun harvest() {
        if (!hasPermission()) return
        val now = System.currentTimeMillis()
        val results: List<ScanResult> = try {
            wifiManager?.scanResults ?: emptyList()
        } catch (se: SecurityException) {
            lastError = "Scan results denied: ${se.message}"
            emptyList()
        } catch (t: Throwable) {
            lastError = t.message
            emptyList()
        }
        if (results.isEmpty()) return
        synchronized(lock) {
            scanCount++
            lastScanMs = now
            for (r in results) {
                val bssid = r.BSSID ?: continue
                if (!firstSeen.containsKey(bssid)) {
                    firstSeen[bssid] = now
                    appearanceTimes.addLast(now)
                }
                val st = links.getOrPut(bssid) { LinkState() }
                st.add(r.level, now)
            }
        }
        lastError = null
    }

    /** Recomputes the published metrics; call on the fusion tick. */
    fun tick(nowMs: Long): WifiMetrics = synchronized(lock) {
        // prune
        val cutoff = nowMs - LINK_RETENTION_MS
        val it = links.iterator()
        while (it.hasNext()) {
            if (it.next().value.lastSeenMs < cutoff) it.remove()
        }
        val fsIt = firstSeen.iterator()
        while (fsIt.hasNext()) {
            val e = fsIt.next()
            val st = links[e.key]
            if (st == null || st.lastSeenMs < cutoff) fsIt.remove()
        }
        while (appearanceTimes.isNotEmpty() && nowMs - appearanceTimes.first() > 60_000L) {
            appearanceTimes.removeFirst()
        }

        val stds = ArrayList<Double>()
        val cvs = ArrayList<Double>()
        var strong = 0
        var activeLinks = 0
        var dipTotal = 0.0
        for (st in links.values) {
            val s = st.stats() ?: continue
            stds.add(s.stdDb)
            cvs.add(s.cv)
            if (st.lastRssi >= STRONG_RSSI) strong++
            if (s.cv >= ACTIVE_CV) activeLinks++
            dipTotal += s.dipRatePerMin
        }

        val stdMedian = medianOf(stds).toFloat()
        val stdP90 = percentileOf(stds, 0.90).toFloat()
        val cvMedian = medianOf(cvs).toFloat()
        val cvP90 = percentileOf(cvs, 0.90).toFloat()
        val churn = appearanceTimes.size.toFloat() // per minute by construction

        // Composite turbulence index: bounded, dominated by fast fluctuation, with a
        // smaller contribution from discrete blocking events and arrival churn.
        val activity = (
            cvMedian * 6.0 +
                (dipTotal / 30.0).coerceAtMost(1.5) * 0.25 +
                (churn / 20.0).coerceAtMost(1.5) * 0.15
            ).toFloat()

        return WifiMetrics(
            apCount = links.size,
            strongApCount = strong,
            linksTracked = stds.size,
            rssiStdMedian = stdMedian,
            rssiStdP90 = stdP90,
            cvMedian = cvMedian,
            cvP90 = cvP90,
            dipRatePerMin = dipTotal.toFloat(),
            activeLinkFraction = if (stds.isEmpty()) 0f else activeLinks.toFloat() / stds.size,
            churnPerMin = churn,
            activityIndex = activity,
            scanCount = scanCount,
            lastScanMs = lastScanMs
        )
    }

    fun reset() = synchronized(lock) {
        links.clear()
        firstSeen.clear()
        appearanceTimes.clear()
        scanCount = 0
    }

    // ---------------------------------------------------------------------------

    private class LinkState {
        private val rssi = FloatArray(CAP)
        private val ts = LongArray(CAP)
        private var head = 0
        private var size = 0
        var lastSeenMs = 0L
        var lastRssi = -100

        fun add(level: Int, now: Long) {
            rssi[head] = level.toFloat()
            ts[head] = now
            head = (head + 1) % CAP
            if (size < CAP) size++
            lastSeenMs = now
            lastRssi = level
        }

        /** Per-link statistics over the retained window. */
        fun stats(): LinkStats? {
            if (size < MIN_SAMPLES) return null
            val start = (head - size + CAP) % CAP
            val n = size
            var meanDb = 0.0
            for (k in 0 until n) meanDb += rssi[(start + k) % CAP]
            meanDb /= n

            var varDb = 0.0
            var meanLin = 0.0
            val lin = DoubleArray(n)
            for (k in 0 until n) {
                val v = rssi[(start + k) % CAP].toDouble()
                val d = v - meanDb
                varDb += d * d
                val p = dbmToLinear(v)
                lin[k] = p
                meanLin += p
            }
            varDb /= (n - 1)
            meanLin /= n
            var varLin = 0.0
            for (k in 0 until n) {
                val d = lin[k] - meanLin
                varLin += d * d
            }
            varLin /= (n - 1)
            val stdDb = sqrt(varDb)
            val cv = if (meanLin > 0) sqrt(varLin) / meanLin else 0.0

            // --- blocking ("dip") events, Schmitt-trigger style ---
            val threshold = meanDb - DIP_K * stdDb.coerceAtLeast(0.4)
            val release = meanDb - DIP_RELEASE_K * stdDb.coerceAtLeast(0.4)
            var dips = 0
            var armed = true
            var lastDipMs = -1L
            for (k in 0 until n) {
                val idx = (start + k) % CAP
                val v = rssi[idx]
                val t = ts[idx]
                if (armed && v < threshold && (lastDipMs < 0 || t - lastDipMs > DIP_MIN_GAP_MS)) {
                    dips++
                    armed = false
                    lastDipMs = t
                } else if (!armed && v > release) {
                    armed = true
                }
            }
            val spanMin = ((ts[(start + n - 1) % CAP] - ts[start]) / 60_000.0).coerceAtLeast(0.25)
            val dipRate = if (stdDb < QUIET_STD_DB) 0.0 else dips / spanMin

            return LinkStats(stdDb, cv, dipRate)
        }

        companion object {
            private const val CAP = 96
            private const val MIN_SAMPLES = 6
            private const val DIP_K = 1.2
            private const val DIP_RELEASE_K = 0.5
            private const val DIP_MIN_GAP_MS = 400L
            private const val QUIET_STD_DB = 0.6
        }
    }

    data class LinkStats(val stdDb: Double, val cv: Double, val dipRatePerMin: Double)

    companion object {
        private const val LINK_RETENTION_MS = 300_000L
        private const val STRONG_RSSI = -70
        private const val ACTIVE_CV = 0.25

        /** Rough free-space intuition used by the UI copy only. */
        fun approximateDistance(rssiDbm: Int, freqMhz: Int): Float {
            // FSPL at 1 m for 2.4 GHz ~ 40 dB
            val fspl = if (freqMhz > 3000) 46.0 else 40.0
            val exponent = (fspl - rssiDbm) / 20.0
            return 10.0.pow(exponent).let { abs(it).toFloat() }.coerceIn(0.5f, 200f)
        }

        fun cvToOccupancyHint(cv: Float): Float = (log10(1.0 + (cv * 10.0).toDouble())).toFloat()
    }
}
