package com.ambientsense.app.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ambientsense.app.AmbientApp
import com.ambientsense.app.data.LabelEntity
import com.ambientsense.app.data.SensorBus
import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.sensing.CalibrationModel
import com.ambientsense.app.sensing.NetworkProbe
import com.ambientsense.app.sensing.SensingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ErrorStats(
    val mae: Double = 0.0,
    val rmse: Double = 0.0,
    val bias: Double = 0.0,
    val withinOne: Double = 0.0
)

data class ValidationSummary(
    val labels: Int = 0,
    val people: ErrorStats = ErrorStats(),
    val peopleRules: ErrorStats = ErrorStats(),
    val vehicles: ErrorStats = ErrorStats(),
    val earlyLabels: ErrorStats? = null,
    val recentLabels: ErrorStats? = null,
    /** (truth, estimate) pairs for the calibration scatter, oldest first. */
    val points: List<Pair<Float, Float>> = emptyList()
) {
    /** Relative improvement of the calibrated estimate over the raw rules. */
    val improvementPct: Double
        get() = if (peopleRules.mae > 1e-6) 100.0 * (peopleRules.mae - people.mae) / peopleRules.mae else 0.0
}

data class ModelSummary(
    val people: CalibrationModel.Stats,
    val vehicles: CalibrationModel.Stats,
    val version: Int,
    /** Labels taken at the *current* sensing radius, i.e. the ones being trusted. */
    val peopleTrusted: Int = 0
)

class AmbientViewModel(app: Application) : AndroidViewModel(app) {

    private val amb = app as AmbientApp
    private val dao = amb.database.dao()

    val settings: StateFlow<SenseSettings> = amb.settings.flow

    val running = SensorBus.running
    val wifi = SensorBus.wifi
    val ble = SensorBus.ble
    val noise = SensorBus.noise
    val net = SensorBus.net
    val fusion = SensorBus.fusion
    val location = SensorBus.location
    val samples = SensorBus.samples
    val pingHistory = SensorBus.pingHistory
    val downHistory = SensorBus.downHistory
    val status = SensorBus.status
    val sessionStartMs = SensorBus.sessionStartMs

    private val _modelVersion = MutableStateFlow(0)
    val modelSummary: StateFlow<ModelSummary> =
        combine(_modelVersion, dao.labelCount()) { version, _ ->
            ModelSummary(amb.models.people.stats(), amb.models.vehicles.stats(), version,
                amb.models.people.trustedLabelCount)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ModelSummary(amb.models.people.stats(), amb.models.vehicles.stats(), 0,
                amb.models.people.trustedLabelCount)
        )

    val labels: StateFlow<List<LabelEntity>> =
        dao.recentLabels(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Each label stores the estimate the app was showing *before* the label was
     * folded in, so these pairs are a genuine prequential (one-step-ahead) error
     * series - the honest accuracy number for this phone in this place.
     */
    val validation: StateFlow<ValidationSummary> =
        combine(labels, _modelVersion) { list, _ -> summarise(list) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ValidationSummary())

    private fun summarise(list: List<LabelEntity>): ValidationSummary {
        if (list.isEmpty()) return ValidationSummary()
        val n = list.size
        fun stats(errs: List<Double>): ErrorStats {
            val mae = errs.map { abs(it) }.average()
            val rmse = sqrt(errs.map { it * it }.average())
            val bias = errs.average()
            return ErrorStats(mae, rmse, bias, errs.count { abs(it) <= 1.0 }.toDouble() / errs.size)
        }
        val peopleErr = list.map { it.peopleEstimate.toDouble() - it.peopleTruth }
        val rulesErr = list.map { it.peopleRules.toDouble() - it.peopleTruth }
        val vehErr = list.map { it.vehicleEstimate.toDouble() - it.vehicleTruth }
        // learning curve: error in the first half of the labels vs the most recent half
        val half = n / 2
        val early = if (half >= 3) stats(peopleErr.take(half)) else null
        val late = if (half >= 3) stats(peopleErr.takeLast(half)) else null
        return ValidationSummary(
            labels = n,
            people = stats(peopleErr),
            peopleRules = stats(rulesErr),
            vehicles = stats(vehErr),
            earlyLabels = early,
            recentLabels = late,
            points = list.takeLast(40).map { it.peopleTruth.toFloat() to it.peopleEstimate }
        )
    }

    private val _probeRunning = MutableStateFlow(false)
    val probeRunning: StateFlow<Boolean> = _probeRunning

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun consumeToast() { _toast.value = null }

    // --------------------------------------------------------------- session

    fun startSensing() {
        SensingService.start(getApplication())
    }

    fun stopSensing() {
        SensingService.stop(getApplication())
    }

    // ------------------------------------------------------------ calibration

    fun submitLabel(people: Int, vehicles: Int, noiseDba: Float?, note: String?) {
        viewModelScope.launch {
            val (rulesPeople, rulesVehicles) = amb.fusion.submitLabel(
                truthPeople = people,
                truthVehicles = vehicles,
                wifi = SensorBus.wifi.value,
                ble = SensorBus.ble.value,
                noise = SensorBus.noise.value,
                net = SensorBus.net.value,
                nowMs = System.currentTimeMillis()
            )
            val fix = SensorBus.location.value
            val label = LabelEntity(
                t = System.currentTimeMillis(),
                lat = fix?.lat,
                lon = fix?.lon,
                peopleTruth = people,
                vehicleTruth = vehicles,
                noiseTruthDba = noiseDba,
                peopleEstimate = SensorBus.fusion.value.people,
                vehicleEstimate = SensorBus.fusion.value.vehicles,
                peopleRules = rulesPeople,
                vehicleRules = rulesVehicles,
                note = note,
                features = amb.fusion.featureCsv(
                    SensorBus.wifi.value, SensorBus.ble.value, SensorBus.noise.value, System.currentTimeMillis()
                )
            )
            withContext(Dispatchers.IO) { dao.insertLabel(label) }
            _modelVersion.value++
            val delta = (people - rulesPeople).let { String.format(Locale.US, "%+.1f", it) }
            _toast.value = "Learned from your count (rules were $delta off)"
        }
    }

    fun deleteLabel(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteLabel(id) }
            _modelVersion.value++
        }
    }

    fun clearLabels() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.clearLabels() }
            _modelVersion.value++
        }
    }

    fun resetCalibration() {
        amb.models.reset()
        _modelVersion.value++
        _toast.value = "Calibration reset — the app is back to its physics baseline"
    }

    // ----------------------------------------------------------------- probes

    fun runProbeNow() {
        if (_probeRunning.value) return
        viewModelScope.launch {
            _probeRunning.value = true
            val ctx = getApplication<Application>()
            val probe = NetworkProbe(ctx)
            val s = amb.settings.current
            val ping = probe.ping(s.pingHost, s.pingCount)
            SensorBus.setNet(SensorBus.net.value.copy(ping = ping, updatedMs = System.currentTimeMillis()))
            ping.avgMs?.let { SensorBus.addPing(it) }
            val down = probe.download(s.speedTestUrl, maxMs = 12_000L)
            SensorBus.setNet(SensorBus.net.value.copy(download = down, updatedMs = System.currentTimeMillis()))
            down.mbps?.let { SensorBus.addDownload(it) }
            if (s.runUploadTest) {
                val up = probe.upload(s.uploadUrl, s.uploadBytes)
                SensorBus.setNet(SensorBus.net.value.copy(upload = up))
            }
            _probeRunning.value = false
        }
    }

    // ------------------------------------------------------------------ export

    suspend fun exportSamplesCsv(): Uri? = withContext(Dispatchers.IO) {
        val rows = dao.samplesBetween(0L, Long.MAX_VALUE)
        if (rows.isEmpty()) return@withContext null
        val file = writeCsv("ambient-samples") { w ->
            w.appendLine(
                "iso_time,epoch_ms,lat,lon,accuracy_m,people,vehicles,people_rules,vehicle_rules," +
                    "confidence,wifi_ap,wifi_cv_median,wifi_dip_rate,wifi_churn,ble_entities,ble_nearby," +
                    "ble_pedestrian,ble_vehicle,ble_static,noise_dba,down_mbps,up_mbps,ping_ms,jitter_ms," +
                    "loss_pct,net_type,rsrp_dbm"
            )
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            rows.forEach { s ->
                w.appendLine(
                    listOf(
                        iso.format(Date(s.t)), s.t, s.lat ?: "", s.lon ?: "", s.accuracyM ?: "",
                        s.people, s.vehicles, s.peopleRules, s.vehicleRules, s.confidence,
                        s.wifiApCount, s.wifiCvMedian, s.wifiDipRate, s.wifiChurn,
                        s.bleEntities, s.bleNearby, s.blePedestrian, s.bleVehicle, s.bleStatic,
                        s.noiseDba ?: "", s.downMbps ?: "", s.upMbps ?: "", s.pingMs ?: "",
                        s.jitterMs ?: "", s.lossPct ?: "", s.netType ?: "", s.rsrpDbm ?: ""
                    ).joinToString(",")
                )
            }
        }
        shareUri(file)
    }

    suspend fun exportLabelsCsv(): Uri? = withContext(Dispatchers.IO) {
        val rows: List<LabelEntity> = try {
            dao.recentLabels(Int.MAX_VALUE).first()
        } catch (t: Throwable) {
            emptyList()
        }
        if (rows.isEmpty()) return@withContext null
        val file = writeCsv("ambient-labels") { out ->
            out.appendLine(
                "iso_time,epoch_ms,lat,lon,people_truth,vehicle_truth,noise_truth_dba," +
                    "people_estimate,vehicle_estimate,note,features"
            )
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            rows.forEach { label ->
                val note = label.note?.replace(",", " ") ?: ""
                out.appendLine(
                    listOf<Any>(
                        iso.format(Date(label.t)), label.t,
                        label.lat ?: "", label.lon ?: "",
                        label.peopleTruth, label.vehicleTruth,
                        label.noiseTruthDba ?: "",
                        label.peopleEstimate, label.vehicleEstimate,
                        note, "\"${label.features}\""
                    ).joinToString(",")
                )
            }
        }
        shareUri(file)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearSamples()
        SensorBus.setSamples(emptyList(), 0)
    }

    fun updateSettings(mutator: (SenseSettings) -> SenseSettings) {
        amb.settings.update(mutator)
    }

    /**
     * Change how far out the app reports on.
     *
     * Measured in validation (`radius.py`, E8c): a calibration learned at one radius
     * transfers badly to another — across every pair we tested it was *worse* than
     * the plain physics baseline. So the model keeps its weights as a warm start but
     * its trust drops to zero and is re-earned by labels at the new radius. Nothing
     * the user labelled is thrown away.
     */
    fun setSensingRadius(meters: Float) {
        val current = amb.settings.current
        if (abs(current.proximityRadiusM - meters) < 0.5f) return
        amb.settings.update { it.copy(proximityRadiusM = meters, vehicleRadiusM = meters) }
        amb.models.decayTrust()
        _modelVersion.value++
        _toast.value = "Radius ${meters.roundToInt()} m — re-label a few times for this range"
    }

    // ------------------------------------------------------------------ helpers

    private fun writeCsv(prefix: String, block: (java.io.BufferedWriter) -> Unit): File {
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir, "$prefix-$stamp.csv")
        f.bufferedWriter().use(block)
        return f
    }

    private fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.files", file)

}
