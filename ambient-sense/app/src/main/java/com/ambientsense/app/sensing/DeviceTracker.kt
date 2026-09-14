package com.ambientsense.app.sensing

import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.model.BleMetrics
import com.ambientsense.app.model.DeviceTrack
import com.ambientsense.app.model.MotionClass
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns a stream of BLE advertisements into *tracked entities* with a range, a speed
 * and a motion class.
 *
 * v0.9 fitted a straight line to range-vs-time. That is the wrong model: something
 * driving past you has a **hyperbolic** range curve,
 *
 *      r(t)^2 = r0^2 + v^2 (t - t0)^2
 *
 * and a straight line through a symmetric V averages out to ~0 m/s — which is why the
 * first version reported cars as "standing". This version fits the pass model instead,
 * with three statistical guards that were tuned in `validation/`:
 *
 *  - **BIC model selection** decides whether the object is moving at all, comparing
 *    the 3-parameter pass model against a 1-parameter constant-range model. The
 *    penalty uses an *effective* sample size derived from the residual
 *    autocorrelation, because RSSI noise is temporally correlated and a smooth wander
 *    can otherwise look exactly like a pass.
 *  - **Significance gate**: |v| must exceed staticThreshold + k·σ_v, where σ_v comes
 *    from the regression. Otherwise the "motion" is fitted noise.
 *  - **Adaptive gate**: a car is inside BLE range for ~3 s, so a clean, fast fit is
 *    accepted after 3 samples instead of waiting for the usual dwell.
 *
 * Devices travelling together (a car's head unit + phone, a person's phone + watch)
 * are clustered with union-find so they count once. Devices that have sat perfectly
 * still for minutes are **infrastructure** (laptop, TV, beacon, parked car) and are
 * reported separately instead of being counted as people.
 */
class DeviceTracker(private val settings: () -> SenseSettings) {

    private val lock = Any()
    private val tracks = HashMap<String, Track>()

    private var vehiclesPassed = 0
    private var pedestriansPassed = 0
    private var scanCount = 0
    private var lastResultMs = 0L
    private var nextGroupId = 1

    // ------------------------------------------------------------------ ingestion

    fun onSighting(
        key: String,
        name: String?,
        rssi: Int,
        txPower: Int,
        manufacturerId: Int,
        randomizedMac: Boolean,
        nowMs: Long
    ) {
        synchronized(lock) {
            scanCount++
            lastResultMs = nowMs
            val tr = tracks.getOrPut(key) { Track(key).also { it.firstSeenMs = nowMs } }
            if (!name.isNullOrBlank()) tr.name = name
            if (txPower > MIN_VALID_TX && txPower < 20) tr.txPower = txPower
            if (manufacturerId >= 0) tr.manufacturerId = manufacturerId
            tr.randomizedMac = randomizedMac
            tr.totalSamples++
            tr.lastSeenMs = nowMs
            tr.rssiEma = if (tr.totalSamples == 1) rssi.toFloat()
            else tr.rssiEma + RSSI_ALPHA * (rssi - tr.rssiEma)

            val s = settings()
            val tx = if (tr.txPower != Int.MIN_VALUE) tr.txPower else s.defaultTxPower
            val d = rangeMeters(tr.rssiEma, tx, s.pathLossExponent)
            tr.push(nowMs / 1000.0, d.toDouble())
            tr.distanceM = d
        }
    }

    fun noteScan() {
        synchronized(lock) { scanCount++ }
    }

    // --------------------------------------------------------------------- ticking

    fun tick(nowMs: Long): BleMetrics {
        val s = settings()
        val nowSec = nowMs / 1000.0
        synchronized(lock) {
            val it = tracks.values.iterator()
            while (it.hasNext()) {
                val tr = it.next()
                if (nowMs - tr.lastSeenMs > EXPIRE_MS) {
                    if (tr.totalSamples >= s.minTrackSamples) {
                        when (tr.motion) {
                            MotionClass.VEHICLE -> vehiclesPassed++
                            MotionClass.PEDESTRIAN -> pedestriansPassed++
                            else -> Unit
                        }
                    }
                    it.remove()
                } else {
                    tr.refresh(nowSec, s)
                    tr.infrastructure = computeInfrastructure(tr, nowSec, s)
                }
            }

            val active = tracks.values.filter { nowMs - it.lastSeenMs <= ACTIVE_MS }
            assignGroups(active, s)

            val entityList = groupTracks(active)
            var near = 0
            active.forEach { if (it.distanceM <= s.proximityRadiusM) near++ }
            val background = entityList.count { it.first.infrastructure }

            return BleMetrics(
                tracks = active.sortedBy { it.distanceM }.map { it.toModel(nowMs) },
                totalTracked = active.size,
                nearCount = near,
                staticCount = entityList.count { it.first.motion == MotionClass.STATIC },
                pedestrianCount = entityList.count { it.first.motion == MotionClass.PEDESTRIAN },
                vehicleCount = entityList.count { it.first.motion == MotionClass.VEHICLE },
                unknownCount = entityList.count { it.first.motion == MotionClass.UNKNOWN },
                randomizedCount = active.count { it.randomizedMac },
                entityCount = entityList.size,
                backgroundCount = background,
                vehiclesPassed = vehiclesPassed,
                pedestriansPassed = pedestriansPassed,
                scanCount = scanCount,
                lastResultMs = lastResultMs
            )
        }
    }

    fun reset() {
        synchronized(lock) {
            tracks.clear()
            vehiclesPassed = 0
            pedestriansPassed = 0
            scanCount = 0
            nextGroupId = 1
        }
    }

    // ------------------------------------------------------------------ clustering

    private fun assignGroups(active: List<Track>, s: SenseSettings) {
        active.forEach { it.groupId = -1 }
        if (!s.groupDevices) return

        val parent = HashMap<String, String>()
        fun find(a: String): String {
            var r = a
            while (parent[r] != r) r = parent[r]!!
            return r
        }
        active.forEach { parent[it.key] = it.key }

        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]
                val b = active[j]
                val maxSlope = maxOf(abs(a.slopeMps), abs(b.slopeMps))
                val slopeClose = abs(a.slopeMps - b.slopeMps) <= SLOPE_TOLERANCE_FRAC * maxSlope + SLOPE_TOLERANCE_ABS
                val sameDirection = (a.slopeMps >= 0) == (b.slopeMps >= 0)
                val distanceClose = abs(a.distanceM - b.distanceM) <=
                    if (maxSlope < s.staticSpeedMps) STATIC_GROUP_RADIUS_M else MOVING_GROUP_RADIUS_M
                val bothConfident = a.samples >= s.minTrackSamples && b.samples >= s.minTrackSamples
                if (slopeClose && sameDirection && distanceClose && bothConfident) {
                    val ra = find(a.key)
                    val rb = find(b.key)
                    if (ra != rb) parent[ra] = rb
                }
            }
        }

        val ids = HashMap<String, Int>()
        active.forEach { tr ->
            tr.groupId = ids.getOrPut(find(tr.key)) { nextGroupId++ }
        }
        val counts = HashMap<Int, Int>()
        active.forEach { counts[it.groupId] = (counts[it.groupId] ?: 0) + 1 }
        active.forEach { if (counts[it.groupId] == 1) it.groupId = -1 }
    }

    /** Lead device per cluster: the one with the largest |speed|. */
    private fun groupTracks(active: List<Track>): List<Pair<Track, List<Track>>> {
        val buckets = LinkedHashMap<String, MutableList<Track>>()
        active.forEach { t ->
            val key = if (t.groupId >= 0) "g${t.groupId}" else "s${t.key}"
            buckets.getOrPut(key) { ArrayList() }.add(t)
        }
        return buckets.values.map { members ->
            (members.maxByOrNull { abs(it.slopeMps) } ?: members.first()) to members
        }
    }

    /** Long-lived and perfectly still => a laptop or a parked car, not a person. */
    private fun computeInfrastructure(tr: Track, nowSec: Double, s: SenseSettings): Boolean {
        if (!s.excludeInfrastructure) return false
        if (tr.motion != MotionClass.STATIC) return false
        if ((nowSec - tr.firstSeenMs / 1000.0) < s.infrastructureDwellSec) return false
        val recent = tr.recentRanges(nowSec, 30.0)
        if (recent.size < 5) return false
        return stdDev(recent) < s.infrastructureRangeStdM
    }

    companion object {
        private const val CAP = 128
        private const val HISTORY_POINTS = 40
        private const val FIT_WINDOW_SEC = 10.0
        private const val RSSI_ALPHA = 0.25f
        private const val MIN_FIT_R2 = 0.18f
        private const val CLASS_DWELL_MS = 900L
        private const val EXPIRE_MS = 14_000L
        private const val ACTIVE_MS = 5_000L
        private const val MIN_VALID_TX = -127
        private const val SLOPE_TOLERANCE_FRAC = 0.35f
        private const val SLOPE_TOLERANCE_ABS = 0.30f
        private const val MOVING_GROUP_RADIUS_M = 3.5f
        private const val STATIC_GROUP_RADIUS_M = 1.5f

        /** t0 search grid: how many candidate closest-approach times. */
        private const val T0_GRID = 25
        /** t0 is only allowed this far outside the observed span. */
        private const val T0_MARGIN_SEC = 2.0

        fun rangeMeters(rssi: Float, txPowerDbm: Int, pathLossExponent: Float): Float {
            val n = if (pathLossExponent < 1f) 1f else pathLossExponent
            val d = 10f.pow((txPowerDbm - rssi) / (10f * n))
            return d.coerceIn(0.3f, 150f)
        }

        private fun stdDev(values: List<Double>): Double {
            if (values.size < 2) return 0.0
            val m = values.average()
            return sqrt(values.sumOf { (it - m) * (it - m) } / (values.size - 1))
        }
    }

    // ---------------------------------------------------------------------- model

    private class Track(val key: String) {
        var name: String? = null
        var manufacturerId: Int = -1
        var txPower: Int = Int.MIN_VALUE
        var rssiEma: Float = -100f
        var distanceM: Float = 60f
        var slopeMps: Float = 0f
        var speedSigmaMps: Float = Float.POSITIVE_INFINITY
        var fitR2: Float = 0f
        var samples: Int = 0
        var totalSamples: Int = 0
        var firstSeenMs: Long = 0L
        var lastSeenMs: Long = 0L
        var motion: MotionClass = MotionClass.UNKNOWN
        var candidate: MotionClass = MotionClass.UNKNOWN
        var candidateSinceMs: Long = 0L
        var motionSinceMs: Long = 0L
        var randomizedMac: Boolean = false
        var groupId: Int = -1
        var infrastructure: Boolean = false
        var passMoving: Boolean = false
        var closestRangeM: Float = 0f
        var closestApproachSec: Double = 0.0

        private val tArr = DoubleArray(CAP)
        private val dArr = DoubleArray(CAP)
        private var head = 0
        private var size = 0

        fun push(tSec: Double, dist: Double) {
            tArr[head] = tSec
            dArr[head] = dist
            head = (head + 1) % CAP
            if (size < CAP) size++
        }

        /** Ranges recorded within the last [windowSec] seconds. */
        fun recentRanges(nowSec: Double, windowSec: Double): List<Double> {
            val out = ArrayList<Double>()
            val start = (head - size + CAP) % CAP
            for (k in 0 until size) {
                val idx = (start + k) % CAP
                if (nowSec - tArr[idx] <= windowSec) out.add(dArr[idx])
            }
            return out
        }

        fun refresh(nowSec: Double, s: SenseSettings) {
            // compact the ring into the fit window
            val n: Int
            val xs: DoubleArray
            val rs: DoubleArray
            run {
                val tmpT = DoubleArray(CAP)
                val tmpR = DoubleArray(CAP)
                var k = 0
                val start = (head - size + CAP) % CAP
                for (i in 0 until size) {
                    val idx = (start + i) % CAP
                    if (nowSec - tArr[idx] <= s.fitWindowSec) {
                        tmpT[k] = tArr[idx]
                        tmpR[k] = dArr[idx]
                        k++
                    }
                }
                n = k
                xs = tmpT
                rs = tmpR
            }
            samples = n
            if (n >= 3) fitPass(xs, rs, n, s) else {
                slopeMps = 0f
                fitR2 = 0f
                speedSigmaMps = Float.POSITIVE_INFINITY
                passMoving = false
            }
            if (n >= 1) distanceM = rs[n - 1].toFloat()
            classify(nowSec, s)
        }

        /**
         * Straight-line pass model:  r^2 = r0^2 + v^2 (t - t0)^2
         *
         * For a fixed t0 the model is linear in (r0^2, v^2) with regressor
         * u = (t - t0)^2, so each candidate t0 is solved in closed form. All
         * residuals — and the static-vs-moving decision — are evaluated in range
         * space (metres), never in squared-range space, so the two models stay
         * comparable.
         */
        private fun fitPass(xs: DoubleArray, rs: DoubleArray, n: Int, s: SenseSettings) {
            // y = r^2
            var ybar = 0.0
            for (i in 0 until n) ybar += rs[i] * rs[i]
            ybar /= n

            val tFirst = xs[0]
            val tLast = xs[n - 1]
            var bestSse = Double.POSITIVE_INFINITY
            var bestSlope = 0.0
            var bestIntercept = 0.0
            var bestT0 = tLast
            var bestDenom = 0.0
            var bestResidY = 0.0

            for (g in 0 until T0_GRID) {
                val t0 = tFirst - T0_MARGIN_SEC +
                    (tLast + T0_MARGIN_SEC - (tFirst - T0_MARGIN_SEC)) * g / (T0_GRID - 1)
                // u = (t - t0)^2
                var uSum = 0.0
                for (i in 0 until n) {
                    val d = xs[i] - t0
                    uSum += d * d
                }
                val uBar = uSum / n
                var sxy = 0.0
                var suu = 0.0
                for (i in 0 until n) {
                    val d = xs[i] - t0
                    val u = d * d
                    val du = u - uBar
                    sxy += du * (rs[i] * rs[i] - ybar)
                    suu += du * du
                }
                if (suu < 1e-9) continue
                val slope = sxy / suu
                if (slope < 0.0) continue
                val intercept = ybar - slope * uBar

                // residual sum of squares in RANGE space
                var sse = 0.0
                var residY = 0.0
                for (i in 0 until n) {
                    val d = xs[i] - t0
                    val u = d * d
                    val rHat = sqrt(max(intercept + slope * u, 0.0))
                    val dr = rs[i] - rHat
                    sse += dr * dr
                    val dy = rs[i] * rs[i] - (intercept + slope * u)
                    residY += dy * dy
                }
                if (sse < bestSse) {
                    bestSse = sse
                    bestSlope = slope
                    bestIntercept = intercept
                    bestT0 = t0
                    bestDenom = suu
                    bestResidY = residY
                }
            }
            if (bestSse == Double.POSITIVE_INFINITY) {
                slopeMps = 0f
                fitR2 = 0f
                passMoving = false
                speedSigmaMps = Float.POSITIVE_INFINITY
                return
            }

            // static model: constant range
            var rBar = 0.0
            for (i in 0 until n) rBar += rs[i]
            rBar /= n
            var sseStatic = 0.0
            for (i in 0 until n) {
                val d = rs[i] - rBar
                sseStatic += d * d
            }

            // effective sample size from the lag-1 autocorrelation of residuals
            var rho = 0.0
            if (n > 3 && sseStatic > 1e-9) {
                var num = 0.0
                var den = 0.0
                for (i in 0 until n - 1) {
                    val a = rs[i] - sqrt(max(bestIntercept + bestSlope * (xs[i] - bestT0).pow(2), 0.0))
                    val b = rs[i + 1] - sqrt(max(bestIntercept + bestSlope * (xs[i + 1] - bestT0).pow(2), 0.0))
                    num += a * b
                    den += a * a
                }
                if (den > 1e-12) rho = (num / den).coerceIn(-0.9, 0.95)
            }
            val nEff = (n * (1.0 - rho) / (1.0 + rho)).coerceIn(3.0, n.toDouble())

            passMoving = if (s.useBic) {
                val bicStatic = nEff * ln(max(sseStatic, 1e-9) / n) + ln(nEff)
                val bicPass = nEff * ln(max(bestSse, 1e-9) / n) + 3.0 * ln(nEff)
                bicPass < bicStatic
            } else true

            if (!passMoving) {
                slopeMps = 0f
                fitR2 = 0f
                speedSigmaMps = Float.POSITIVE_INFINITY
                closestRangeM = rBar.toFloat()
                closestApproachSec = tLast
                return
            }

            slopeMps = sqrt(max(bestSlope, 0.0)).toFloat()
            closestRangeM = sqrt(max(bestIntercept, 0.0)).toFloat()
            closestApproachSec = bestT0

            // sigma_v from the y-space regression: v = sqrt(b) => se_v = se_b / (2 v)
            val dof = max(n - 2, 1)
            val sigmaY2 = bestResidY / dof
            val seB = sqrt(sigmaY2 / max(bestDenom, 1e-9))
            speedSigmaMps = if (slopeMps > 1e-6f) (seB / (2.0 * slopeMps)).toFloat()
            else Float.POSITIVE_INFINITY

            fitR2 = if (sseStatic > 1e-9) (1.0 - bestSse / sseStatic).coerceIn(0.0, 1.0).toFloat() else 0f
        }

        private fun classify(nowSec: Double, s: SenseSettings) {
            val nowMs = (nowSec * 1000).toLong()
            val speed = abs(slopeMps)

            // A car is only inside BLE range for ~3 s. When the fit is clean and the
            // speed is decisively high, do not wait for the normal sample count.
            if (s.adaptiveGate && passMoving && samples >= 3 && fitR2 >= 0.5f &&
                speed > s.vehicleMinMps * 1.4f
            ) {
                motion = MotionClass.VEHICLE
                candidate = MotionClass.VEHICLE
                candidateSinceMs = nowMs
                return
            }

            if (samples < s.minTrackSamples || fitR2 < MIN_FIT_R2) return

            // Is the speed bigger than its own standard error?
            val sigma = speedSigmaMps
            val significant = if (sigma.isFinite()) {
                speed > s.staticSpeedMps + s.speedSigmaK * sigma
            } else {
                speed > s.staticSpeedMps
            }

            val next = when {
                !significant -> MotionClass.STATIC
                speed <= s.pedestrianMaxMps -> MotionClass.PEDESTRIAN
                else -> MotionClass.VEHICLE
            }

            if (next != candidate) {
                candidate = next
                candidateSinceMs = nowMs
            }
            val strongEvidence = fitR2 > 0.6f && when (next) {
                MotionClass.STATIC -> speed < s.staticSpeedMps * 0.5f
                MotionClass.PEDESTRIAN -> speed > s.staticSpeedMps * 1.6f && speed < s.pedestrianMaxMps * 0.8f
                MotionClass.VEHICLE -> speed > s.vehicleMinMps * 1.25f
                else -> false
            }
            if (next != motion && (strongEvidence || nowMs - candidateSinceMs >= CLASS_DWELL_MS)) {
                motion = next
                motionSinceMs = nowMs
            }
        }

        fun toModel(nowMs: Long): DeviceTrack {
            val hist = ArrayList<Float>(HISTORY_POINTS)
            val start = (head - size + CAP) % CAP
            val from = (size - HISTORY_POINTS).coerceAtLeast(0)
            for (k in from until size) {
                hist.add(dArr[(start + k) % CAP].toFloat())
            }
            return DeviceTrack(
                key = key,
                displayName = name ?: key,
                rssi = rssiEma.toInt(),
                txPower = if (txPower == Int.MIN_VALUE) Int.MIN_VALUE else txPower,
                distanceM = distanceM,
                radialSpeedMps = slopeMps,
                speedSigmaMps = speedSigmaMps,
                fitR2 = fitR2,
                motion = motion,
                firstSeenMs = firstSeenMs,
                lastSeenMs = lastSeenMs,
                samples = samples,
                randomizedMac = randomizedMac,
                groupId = groupId,
                history = hist,
                manufacturerId = manufacturerId,
                infrastructure = infrastructure,
                closestRangeM = closestRangeM
            )
        }

        private fun Double.pow2() = this * this
    }
}
