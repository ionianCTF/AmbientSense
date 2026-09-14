package com.ambientsense.app.data

import com.ambientsense.app.model.AmbientSample
import com.ambientsense.app.model.BleMetrics
import com.ambientsense.app.model.FusionResult
import com.ambientsense.app.model.GeoFix
import com.ambientsense.app.model.NetworkMetrics
import com.ambientsense.app.model.NoiseMetrics
import com.ambientsense.app.model.WifiMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bus between the long lived [com.ambientsense.app.sensing.SensingService]
 * and the Compose UI. Single process app, so a singleton is safe and cheap.
 */
object SensorBus {

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _wifi = MutableStateFlow(WifiMetrics.EMPTY)
    val wifi: StateFlow<WifiMetrics> = _wifi.asStateFlow()

    private val _ble = MutableStateFlow(BleMetrics.EMPTY)
    val ble: StateFlow<BleMetrics> = _ble.asStateFlow()

    private val _noise = MutableStateFlow(NoiseMetrics.EMPTY)
    val noise: StateFlow<NoiseMetrics> = _noise.asStateFlow()

    private val _net = MutableStateFlow(NetworkMetrics.EMPTY)
    val net: StateFlow<NetworkMetrics> = _net.asStateFlow()

    private val _fusion = MutableStateFlow(FusionResult.EMPTY)
    val fusion: StateFlow<FusionResult> = _fusion.asStateFlow()

    private val _location = MutableStateFlow<GeoFix?>(null)
    val location: StateFlow<GeoFix?> = _location.asStateFlow()

    private val _samples = MutableStateFlow<List<AmbientSample>>(emptyList())
    val samples: StateFlow<List<AmbientSample>> = _samples.asStateFlow()

    /** Recent ping RTTs in ms, oldest first. */
    private val _pingHistory = MutableStateFlow<List<Float>>(emptyList())
    val pingHistory: StateFlow<List<Float>> = _pingHistory.asStateFlow()

    /** Recent download results in Mbps, oldest first. */
    private val _downHistory = MutableStateFlow<List<Float>>(emptyList())
    val downHistory: StateFlow<List<Float>> = _downHistory.asStateFlow()

    private val _sessionStartMs = MutableStateFlow(0L)
    val sessionStartMs: StateFlow<Long> = _sessionStartMs.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setRunning(v: Boolean, startedAtMs: Long = 0L) {
        _running.value = v
        if (v) _sessionStartMs.value = startedAtMs else resetSession()
    }

    fun setWifi(v: WifiMetrics) { _wifi.value = v }
    fun setBle(v: BleMetrics) { _ble.value = v }
    fun setNoise(v: NoiseMetrics) { _noise.value = v }
    fun setNet(v: NetworkMetrics) { _net.value = v }
    fun setFusion(v: FusionResult) { _fusion.value = v }
    fun setLocation(v: GeoFix) { _location.value = v }
    fun setStatus(v: String) { _status.value = v }

    fun addPing(ms: Float, max: Int = 120) {
        _pingHistory.value = (_pingHistory.value + ms).takeLast(max)
    }

    fun addDownload(mbps: Float, max: Int = 60) {
        _downHistory.value = (_downHistory.value + mbps).takeLast(max)
    }

    fun addSample(s: AmbientSample, max: Int) {
        _samples.value = (_samples.value + s).takeLast(max)
    }

    fun setSamples(list: List<AmbientSample>, max: Int) {
        _samples.value = list.takeLast(max)
    }

    private fun resetSession() {
        _samples.value = emptyList()
        _pingHistory.value = emptyList()
        _downHistory.value = emptyList()
        _wifi.value = WifiMetrics.EMPTY
        _ble.value = BleMetrics.EMPTY
        _noise.value = NoiseMetrics.EMPTY
        _fusion.value = FusionResult.EMPTY
        _sessionStartMs.value = 0L
    }
}
