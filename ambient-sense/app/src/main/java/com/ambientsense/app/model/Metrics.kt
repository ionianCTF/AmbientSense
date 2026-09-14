package com.ambientsense.app.model

/**
 * Immutable snapshots produced by each sensor module and consumed by the UI.
 * Every module owns one of these and publishes it on a StateFlow.
 */

// ---------------------------------------------------------------------------
// Wi-Fi (device-free sensing of the RF environment)
// ---------------------------------------------------------------------------

data class WifiMetrics(
    /** Distinct BSSIDs seen in the current retention window. */
    val apCount: Int = 0,
    /** Access points heard at -70 dBm or stronger (i.e. physically close). */
    val strongApCount: Int = 0,
    /** Links with enough history to compute statistics. */
    val linksTracked: Int = 0,
    /** Median per-link RSSI standard deviation, in dB. */
    val rssiStdMedian: Float = 0f,
    /** 90th percentile per-link RSSI standard deviation, in dB. */
    val rssiStdP90: Float = 0f,
    /** Median per-link coefficient of variation of linear power (dimensionless). */
    val cvMedian: Float = 0f,
    /** 90th percentile per-link coefficient of variation of linear power. */
    val cvP90: Float = 0f,
    /** Line-of-sight "dip" events per minute, summed over all links. */
    val dipRatePerMin: Float = 0f,
    /** Fraction of tracked links whose fluctuation exceeds the quiet threshold. */
    val activeLinkFraction: Float = 0f,
    /** New BSSIDs appearing per minute — proxy for devices transiting the area. */
    val churnPerMin: Float = 0f,
    /** Composite RF turbulence index (0 = perfectly static ether, 1+ = busy). */
    val activityIndex: Float = 0f,
    val scanCount: Int = 0,
    val lastScanMs: Long = 0L
) {
    companion object {
        val EMPTY = WifiMetrics()
    }
}

// ---------------------------------------------------------------------------
// Bluetooth Low Energy (device based tracking)
// ---------------------------------------------------------------------------

enum class MotionClass(val label: String) {
    UNKNOWN("Unclassified"),
    STATIC("Standing"),
    PEDESTRIAN("Walking"),
    VEHICLE("Vehicle")
}

data class DeviceTrack(
    /** MAC address, or a stable hash if the platform randomised it. */
    val key: String,
    val displayName: String,
    val rssi: Int,
    val txPower: Int,
    /** Path-loss estimated distance in metres. */
    val distanceM: Float,
    /** Fitted radial velocity in m/s (positive = receding, negative = approaching). */
    val radialSpeedMps: Float,
    /** Standard error of the velocity estimate; infinity when not yet meaningful. */
    val speedSigmaMps: Float = Float.POSITIVE_INFINITY,
    /** Goodness of fit of the range-vs-time regression, 0..1. */
    val fitR2: Float,
    val motion: MotionClass,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val samples: Int,
    val randomizedMac: Boolean,
    /** Devices that move together (phone + watch + car kit) share a group id. */
    val groupId: Int = -1,
    /** Recent distance samples, oldest first, for the sparkline. */
    val history: List<Float> = emptyList(),
    val manufacturerId: Int = -1,
    /** Long-lived, perfectly still device: infrastructure rather than a person. */
    val infrastructure: Boolean = false,
    /** Fitted range at the point of closest approach (0 when not moving). */
    val closestRangeM: Float = 0f
)

data class BleMetrics(
    val tracks: List<DeviceTrack> = emptyList(),
    val totalTracked: Int = 0,
    /** Devices currently estimated closer than the proximity radius. */
    val nearCount: Int = 0,
    val staticCount: Int = 0,
    val pedestrianCount: Int = 0,
    val vehicleCount: Int = 0,
    val unknownCount: Int = 0,
    val randomizedCount: Int = 0,
    /** Physical entities after clustering devices that travel together. */
    val entityCount: Int = 0,
    /** Entities classified as building infrastructure and excluded from the crowd. */
    val backgroundCount: Int = 0,
    /** Cumulative counts for the current session. */
    val vehiclesPassed: Int = 0,
    val pedestriansPassed: Int = 0,
    val scanCount: Int = 0,
    val lastResultMs: Long = 0L
) {
    companion object {
        val EMPTY = BleMetrics()
    }
}

// ---------------------------------------------------------------------------
// Acoustic environment
// ---------------------------------------------------------------------------

data class NoiseMetrics(
    /** A-weighted equivalent continuous level, dB(A) re. the calibration offset. */
    val leqDba: Float? = null,
    /** Slow (1 s) exponential level. */
    val slowDba: Float? = null,
    val peakDba: Float? = null,
    /** Rolling minimum over the last minute — the acoustic noise floor. */
    val floorDba: Float? = null,
    /** Highest level observed in the current session. */
    val maxDba: Float? = null,
    val updatedMs: Long = 0L,
    val running: Boolean = false,
    val message: String? = null
) {
    companion object {
        val EMPTY = NoiseMetrics()
    }
}

// ---------------------------------------------------------------------------
// Connectivity
// ---------------------------------------------------------------------------

data class PingResult(
    val host: String = "",
    val minMs: Float? = null,
    val avgMs: Float? = null,
    val maxMs: Float? = null,
    val jitterMs: Float? = null,
    val lossPct: Float? = null,
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val viaTcp: Boolean = false,
    val atMs: Long = 0L
)

data class ThroughputResult(
    val mbps: Float? = null,
    val bytes: Long = 0,
    val durationMs: Long = 0,
    val atMs: Long = 0L,
    val error: String? = null
)

data class CellSnapshot(
    val operator: String? = null,
    val generation: String = "Unknown",
    val detailedTech: String = "",
    val band: String? = null,
    val cellId: String? = null,
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val sinrDb: Int? = null,
    val timingAdvance: Int? = null,
    val neighborCells: Int = 0
)

data class NetworkMetrics(
    val transport: String = "None",
    val networkType: String = "—",
    val isMetered: Boolean = false,
    val validated: Boolean? = null,
    val linkDownMbps: Int? = null,
    val linkUpMbps: Int? = null,
    val wifiSsid: String? = null,
    val wifiRssiDbm: Int? = null,
    val wifiLinkMbps: Int? = null,
    val wifiFreqMhz: Int? = null,
    val wifiStandard: String? = null,
    val cell: CellSnapshot = CellSnapshot(),
    val ping: PingResult = PingResult(),
    val download: ThroughputResult = ThroughputResult(),
    val upload: ThroughputResult = ThroughputResult(),
    val updatedMs: Long = 0L
) {
    companion object {
        val EMPTY = NetworkMetrics()
    }
}

// ---------------------------------------------------------------------------
// Fused output
// ---------------------------------------------------------------------------

data class FusionResult(
    val people: Float = 0f,
    val vehicles: Float = 0f,
    val peopleRaw: Float = 0f,
    val vehiclesRaw: Float = 0f,
    /** 0..1 — how much the estimator trusts itself right now. */
    val confidence: Float = 0f,
    /** Weight of the learned model vs. the physics heuristic, 0..1. */
    val modelWeight: Float = 0f,
    val features: List<FeatureContribution> = emptyList(),
    val note: String = "",
    val atMs: Long = 0L
) {
    companion object {
        val EMPTY = FusionResult()
    }
}

data class FeatureContribution(
    val name: String,
    val value: Float,
    val weight: Float
)

// ---------------------------------------------------------------------------
// Location + persisted samples
// ---------------------------------------------------------------------------

data class GeoFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val provider: String = "?",
    val atMs: Long = 0L
)

data class AmbientSample(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val people: Float,
    val vehicles: Float,
    val noiseDba: Float?,
    val downMbps: Float?,
    val pingMs: Float?,
    val wifiAp: Int,
    val bleDevices: Int
)
