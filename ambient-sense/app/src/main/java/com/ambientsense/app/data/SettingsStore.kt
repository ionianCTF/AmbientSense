package com.ambientsense.app.data

import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which radios take part in crowd estimation. */
enum class SenseMode(val label: String, val usesWifi: Boolean, val usesBle: Boolean) {
    WIFI("Wi‑Fi only", true, false),
    BLE("BLE only", false, true),
    BOTH("Wi‑Fi + BLE", true, true)
}

/** Map overlay themes. */
enum class HeatLayer(val label: String) {
    PEOPLE("People"),
    VEHICLES("Vehicles"),
    NOISE("Noise"),
    THROUGHPUT("Throughput"),
    PING("Latency")
}

enum class TileSource(val label: String) {
    MAPNIK("OSM standard"),
    HIKING("OpenTopo / hiking"),
    CYCLING("CyclOSM"),
    DARK("CARTO dark")
}

data class SenseSettings(
    // ---- radios ----
    val mode: SenseMode = SenseMode.BOTH,
    val wifiScanIntervalMs: Long = 20_000L,
    val bleScanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,

    // ---- RF geometry ----
    val pathLossExponent: Float = 2.6f,
    val defaultTxPower: Int = -59,
    /** Region the app reports on: "how many people are within this radius?" */
    val proximityRadiusM: Float = 30f,
    /** Vehicles are louder, faster and bigger, so they get their own gate. */
    val vehicleRadiusM: Float = 30f,

    // ---- motion classification ----
    // Values retuned against the validation suite (see validation/results/REPORT.md):
    // the pass model recovers short, fast tracks, so the vehicle threshold can drop
    // from 3.0 m/s to 1.6 m/s without losing precision.
    val staticSpeedMps: Float = 0.6f,
    val pedestrianMaxMps: Float = 1.3f,
    val vehicleMinMps: Float = 1.6f,
    val minTrackSamples: Int = 3,
    val groupDevices: Boolean = true,
    val fitWindowSec: Float = 10f,
    /** Gate: |v| must exceed staticSpeed + k * sigma_v before we call it moving. */
    val speedSigmaK: Float = 2.0f,
    /** BIC decides moving-vs-static instead of trusting every fit. */
    val useBic: Boolean = true,
    /** Accept a clean, fast fit before the usual sample count (cars are ~3 s). */
    val adaptiveGate: Boolean = true,
    /** Treat long-lived, perfectly still devices as infrastructure, not people. */
    val excludeInfrastructure: Boolean = true,
    val infrastructureDwellSec: Float = 120f,
    val infrastructureRangeStdM: Float = 0.6f,

    // ---- crowd heuristics ----
    val deviceToPersonFactor: Float = 1.0f,
    val rfPersonCoeff: Float = 3.0f,
    val dipPersonCoeff: Float = 0.30f,
    val vehicleDipCoeff: Float = 0.0f,
    /** A motionless cluster is usually a parked car or a laptop, not a person. */
    val staticEntityWeight: Float = 0.4f,

    // ---- acoustics ----
    val audioEnabled: Boolean = true,
    val micCalibrationDb: Float = 94f,

    // ---- connectivity probes ----
    val pingHost: String = "1.1.1.1",
    val pingCount: Int = 5,
    val pingIntervalMs: Long = 15_000L,
    val speedTestUrl: String = "https://speed.cloudflare.com/__down?bytes=10000000",
    val uploadUrl: String = "https://speed.cloudflare.com/__up",
    val speedTestIntervalMs: Long = 180_000L,
    val runUploadTest: Boolean = false,
    val uploadBytes: Int = 1_000_000,

    // ---- sampling / storage ----
    val fusionIntervalMs: Long = 1_000L,
    val persistIntervalMs: Long = 15_000L,
    val historyWindow: Int = 900,

    // ---- ui ----
    val mapTileSource: TileSource = TileSource.MAPNIK,
    val heatLayer: HeatLayer = HeatLayer.PEOPLE,
    val keepScreenOn: Boolean = true,
    val showUnclassified: Boolean = false
)

/**
 * Tiny typed wrapper over SharedPreferences exposing the settings as a StateFlow
 * so both the UI and the sensing service observe the same source of truth.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ambient_settings", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<SenseSettings> = _flow.asStateFlow()

    val current: SenseSettings get() = _flow.value

    fun update(mutator: (SenseSettings) -> SenseSettings) {
        val next = mutator(_flow.value)
        save(next)
        _flow.value = next
    }

    fun rawPrefs(): SharedPreferences = prefs

    private fun load(): SenseSettings = SenseSettings(
        mode = SenseMode.valueOf(prefs.getString(K_MODE, SenseMode.BOTH.name) ?: SenseMode.BOTH.name),
        wifiScanIntervalMs = prefs.getLong(K_WIFI_INTERVAL, 20_000L),
        bleScanMode = prefs.getInt(K_BLE_MODE, ScanSettings.SCAN_MODE_LOW_LATENCY),
        pathLossExponent = prefs.getFloat(K_PATH_LOSS, 2.6f),
        defaultTxPower = prefs.getInt(K_TX_POWER, -59),
        proximityRadiusM = prefs.getFloat(K_PROX_RADIUS, 30f),
        vehicleRadiusM = prefs.getFloat(K_VEH_RADIUS, 30f),
        staticSpeedMps = prefs.getFloat(K_STATIC_SPEED, 0.6f),
        pedestrianMaxMps = prefs.getFloat(K_PED_SPEED, 1.3f),
        vehicleMinMps = prefs.getFloat(K_VEH_SPEED, 1.6f),
        minTrackSamples = prefs.getInt(K_MIN_SAMPLES, 3),
        groupDevices = prefs.getBoolean(K_GROUP_DEVICES, true),
        fitWindowSec = prefs.getFloat(K_FIT_WINDOW, 10f),
        speedSigmaK = prefs.getFloat(K_SIGMA_K, 2.0f),
        useBic = prefs.getBoolean(K_USE_BIC, true),
        adaptiveGate = prefs.getBoolean(K_ADAPTIVE_GATE, true),
        excludeInfrastructure = prefs.getBoolean(K_EXCLUDE_INFRA, true),
        infrastructureDwellSec = prefs.getFloat(K_INFRA_DWELL, 120f),
        infrastructureRangeStdM = prefs.getFloat(K_INFRA_STD, 0.6f),
        deviceToPersonFactor = prefs.getFloat(K_DEV_PERSON, 1.0f),
        rfPersonCoeff = prefs.getFloat(K_RF_PERSON, 3.0f),
        dipPersonCoeff = prefs.getFloat(K_DIP_PERSON, 0.30f),
        vehicleDipCoeff = prefs.getFloat(K_VEH_DIP, 0.0f),
        staticEntityWeight = prefs.getFloat(K_STATIC_WEIGHT, 0.4f),
        audioEnabled = prefs.getBoolean(K_AUDIO, true),
        micCalibrationDb = prefs.getFloat(K_MIC_CAL, 94f),
        pingHost = prefs.getString(K_PING_HOST, "1.1.1.1") ?: "1.1.1.1",
        pingCount = prefs.getInt(K_PING_COUNT, 5),
        pingIntervalMs = prefs.getLong(K_PING_INTERVAL, 15_000L),
        speedTestUrl = prefs.getString(K_SPEED_URL, "https://speed.cloudflare.com/__down?bytes=10000000")
            ?: "https://speed.cloudflare.com/__down?bytes=10000000",
        uploadUrl = prefs.getString(K_UPLOAD_URL, "https://speed.cloudflare.com/__up")
            ?: "https://speed.cloudflare.com/__up",
        speedTestIntervalMs = prefs.getLong(K_SPEED_INTERVAL, 180_000L),
        runUploadTest = prefs.getBoolean(K_RUN_UPLOAD, false),
        uploadBytes = prefs.getInt(K_UPLOAD_BYTES, 1_000_000),
        fusionIntervalMs = prefs.getLong(K_FUSION_INTERVAL, 1_000L),
        persistIntervalMs = prefs.getLong(K_PERSIST_INTERVAL, 15_000L),
        historyWindow = prefs.getInt(K_HISTORY_WINDOW, 900),
        mapTileSource = TileSource.valueOf(
            prefs.getString(K_TILES, TileSource.MAPNIK.name) ?: TileSource.MAPNIK.name
        ),
        heatLayer = HeatLayer.valueOf(
            prefs.getString(K_HEAT, HeatLayer.PEOPLE.name) ?: HeatLayer.PEOPLE.name
        ),
        keepScreenOn = prefs.getBoolean(K_KEEP_SCREEN, true),
        showUnclassified = prefs.getBoolean(K_SHOW_UNCLASSIFIED, false)
    )

    private fun save(s: SenseSettings) {
        prefs.edit()
            .putString(K_MODE, s.mode.name)
            .putLong(K_WIFI_INTERVAL, s.wifiScanIntervalMs)
            .putInt(K_BLE_MODE, s.bleScanMode)
            .putFloat(K_PATH_LOSS, s.pathLossExponent)
            .putInt(K_TX_POWER, s.defaultTxPower)
            .putFloat(K_PROX_RADIUS, s.proximityRadiusM)
            .putFloat(K_VEH_RADIUS, s.vehicleRadiusM)
            .putFloat(K_STATIC_SPEED, s.staticSpeedMps)
            .putFloat(K_PED_SPEED, s.pedestrianMaxMps)
            .putFloat(K_VEH_SPEED, s.vehicleMinMps)
            .putInt(K_MIN_SAMPLES, s.minTrackSamples)
            .putBoolean(K_GROUP_DEVICES, s.groupDevices)
            .putFloat(K_FIT_WINDOW, s.fitWindowSec)
            .putFloat(K_SIGMA_K, s.speedSigmaK)
            .putBoolean(K_USE_BIC, s.useBic)
            .putBoolean(K_ADAPTIVE_GATE, s.adaptiveGate)
            .putBoolean(K_EXCLUDE_INFRA, s.excludeInfrastructure)
            .putFloat(K_INFRA_DWELL, s.infrastructureDwellSec)
            .putFloat(K_INFRA_STD, s.infrastructureRangeStdM)
            .putFloat(K_DEV_PERSON, s.deviceToPersonFactor)
            .putFloat(K_RF_PERSON, s.rfPersonCoeff)
            .putFloat(K_DIP_PERSON, s.dipPersonCoeff)
            .putFloat(K_VEH_DIP, s.vehicleDipCoeff)
            .putFloat(K_STATIC_WEIGHT, s.staticEntityWeight)
            .putBoolean(K_AUDIO, s.audioEnabled)
            .putFloat(K_MIC_CAL, s.micCalibrationDb)
            .putString(K_PING_HOST, s.pingHost)
            .putInt(K_PING_COUNT, s.pingCount)
            .putLong(K_PING_INTERVAL, s.pingIntervalMs)
            .putString(K_SPEED_URL, s.speedTestUrl)
            .putString(K_UPLOAD_URL, s.uploadUrl)
            .putLong(K_SPEED_INTERVAL, s.speedTestIntervalMs)
            .putBoolean(K_RUN_UPLOAD, s.runUploadTest)
            .putInt(K_UPLOAD_BYTES, s.uploadBytes)
            .putLong(K_FUSION_INTERVAL, s.fusionIntervalMs)
            .putLong(K_PERSIST_INTERVAL, s.persistIntervalMs)
            .putInt(K_HISTORY_WINDOW, s.historyWindow)
            .putString(K_TILES, s.mapTileSource.name)
            .putString(K_HEAT, s.heatLayer.name)
            .putBoolean(K_KEEP_SCREEN, s.keepScreenOn)
            .putBoolean(K_SHOW_UNCLASSIFIED, s.showUnclassified)
            .apply()
    }

    private companion object {
        const val K_MODE = "mode"
        const val K_WIFI_INTERVAL = "wifi_interval_ms"
        const val K_BLE_MODE = "ble_mode"
        const val K_PATH_LOSS = "path_loss"
        const val K_TX_POWER = "tx_power"
        const val K_PROX_RADIUS = "prox_radius"
        const val K_VEH_RADIUS = "veh_radius"
        const val K_STATIC_SPEED = "static_speed"
        const val K_PED_SPEED = "ped_speed"
        const val K_VEH_SPEED = "veh_speed"
        const val K_MIN_SAMPLES = "min_samples"
        const val K_GROUP_DEVICES = "group_devices"
        const val K_DEV_PERSON = "dev_person"
        const val K_RF_PERSON = "rf_person"
        const val K_DIP_PERSON = "dip_person"
        const val K_VEH_DIP = "veh_dip"
        const val K_AUDIO = "audio"
        const val K_MIC_CAL = "mic_cal"
        const val K_PING_HOST = "ping_host"
        const val K_PING_COUNT = "ping_count"
        const val K_PING_INTERVAL = "ping_interval"
        const val K_SPEED_URL = "speed_url"
        const val K_UPLOAD_URL = "upload_url"
        const val K_SPEED_INTERVAL = "speed_interval"
        const val K_RUN_UPLOAD = "run_upload"
        const val K_UPLOAD_BYTES = "upload_bytes"
        const val K_FUSION_INTERVAL = "fusion_interval"
        const val K_PERSIST_INTERVAL = "persist_interval"
        const val K_HISTORY_WINDOW = "history_window"
        const val K_TILES = "tiles"
        const val K_HEAT = "heat"
        const val K_KEEP_SCREEN = "keep_screen"
        const val K_SHOW_UNCLASSIFIED = "show_unclassified"
        const val K_FIT_WINDOW = "fit_window_sec"
        const val K_SIGMA_K = "speed_sigma_k"
        const val K_USE_BIC = "use_bic"
        const val K_ADAPTIVE_GATE = "adaptive_gate"
        const val K_EXCLUDE_INFRA = "exclude_infrastructure"
        const val K_INFRA_DWELL = "infra_dwell_sec"
        const val K_INFRA_STD = "infra_range_std_m"
        const val K_STATIC_WEIGHT = "static_entity_weight"
    }
}
