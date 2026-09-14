package com.ambientsense.app.sensing

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ambientsense.app.AmbientApp
import com.ambientsense.app.MainActivity
import com.ambientsense.app.R
import com.ambientsense.app.data.AmbientDao
import com.ambientsense.app.data.SampleEntity
import com.ambientsense.app.data.SensorBus
import com.ambientsense.app.data.SenseMode
import com.ambientsense.app.model.AmbientSample
import com.ambientsense.app.model.GeoFix
import com.ambientsense.app.model.NetworkMetrics
import com.ambientsense.app.util.formatCount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The long lived foreground service that owns every sensor.
 *
 * Android will throttle Wi-Fi scans, BLE scans and microphone capture for anything
 * that is not in the foreground, so the measurement loop lives here behind a sticky
 * notification. The service only orchestrates: each radio has its own module, and the
 * [FusionEngine] turns their output into people/vehicle estimates on a 1 Hz tick.
 */
class SensingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsJob: Job? = null
    private var loopsJob: Job? = null

    private lateinit var app: AmbientApp
    private lateinit var dao: AmbientDao
    private lateinit var wifiScanner: WifiScanner
    private lateinit var bleScanner: BleScanner
    private lateinit var noiseMeter: NoiseMeter
    private lateinit var networkProbe: NetworkProbe
    private lateinit var fusionEngine: FusionEngine

    private var locationThread: HandlerThread? = null
    private var locationManager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var lastFix: GeoFix? = null
    @Volatile private var lastMode: SenseMode? = null

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun service(): SensingService = this@SensingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate() {
        super.onCreate()
        app = applicationContext as AmbientApp
        dao = app.database.dao()
        wifiScanner = WifiScanner(this) { app.settings.current }
        bleScanner = BleScanner(this) { app.settings.current }
        noiseMeter = NoiseMeter(this) { app.settings.current }
        networkProbe = NetworkProbe(this)
        fusionEngine = FusionEngine({ app.settings.current }, app.models)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundSafely()
        beginSession()
        return START_STICKY
    }

    override fun onDestroy() {
        SensorBus.setStatus("Stopped")
        endSession()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    // ------------------------------------------------------------------- session

    private fun beginSession() {
        if (SensorBus.running.value) return
        val now = System.currentTimeMillis()
        SensorBus.setRunning(true, now)
        SensorBus.setStatus("Starting sensors…")

        acquireWakeLock()
        startLocation()
        networkProbe.startMonitoring()
        applyMode()

        loopsJob?.cancel()
        loopsJob = scope.launch {
            launch { tickLoop() }
            launch { wifiScanLoop() }
            launch { pingLoop() }
            launch { throughputLoop() }
            launch { persistLoop() }
        }

        settingsJob?.cancel()
        settingsJob = scope.launch {
            app.settings.flow
                .map { it.mode to it.audioEnabled }
                .distinctUntilChanged()
                .collect {
                    applyMode()
                }
        }
        SensorBus.setStatus("Measuring")
    }

    private fun endSession() {
        loopsJob?.cancel()
        settingsJob?.cancel()
        loopsJob = null
        settingsJob = null
        runCatching { bleScanner.stop() }
        runCatching { wifiScanner.stop() }
        runCatching { noiseMeter.stop() }
        runCatching { networkProbe.stopMonitoring() }
        stopLocation()
        releaseWakeLock()
        SensorBus.setRunning(false)
        stopForegroundSafely()
    }

    private fun applyMode() {
        val s = app.settings.current
        if (s.mode.usesWifi) {
            wifiScanner.start()
            wifiScanner.requestScan()
        } else {
            wifiScanner.stop()
            wifiScanner.reset()
        }

        if (s.mode.usesBle && lastMode != s.mode) {
            bleScanner.reset()
        }
        lastMode = s.mode
        if (s.mode.usesBle) {
            if (!bleScanner.start()) {
                SensorBus.setStatus(
                    if (bleScanner.bluetoothEnabled()) "BLE blocked: grant Nearby devices"
                    else "BLE blocked: turn Bluetooth on"
                )
            }
        } else {
            bleScanner.stop()
            bleScanner.reset()
        }

        if (s.audioEnabled) {
            if (!noiseMeter.start()) {
                // not fatal — the acoustic channel is optional
            }
        } else {
            noiseMeter.stop()
            noiseMeter.reset()
            SensorBus.setNoise(com.ambientsense.app.model.NoiseMetrics(message = "Disabled"))
        }
    }

    // --------------------------------------------------------------------- loops

    private suspend fun tickLoop() {
        while (scope.isActive) {
            val now = System.currentTimeMillis()
            val s = app.settings.current

            if (s.mode.usesWifi) SensorBus.setWifi(wifiScanner.tick(now))
            if (s.mode.usesBle) SensorBus.setBle(bleScanner.tick(now))
            if (s.audioEnabled) SensorBus.setNoise(noiseMeter.sample(now))

            val fused = fusionEngine.fuse(
                wifi = SensorBus.wifi.value,
                ble = SensorBus.ble.value,
                noise = SensorBus.noise.value,
                net = SensorBus.net.value,
                nowMs = now
            )
            SensorBus.setFusion(fused)

            lastFix?.let { fix ->
                SensorBus.addSample(
                    AmbientSample(
                        t = now,
                        lat = fix.lat,
                        lon = fix.lon,
                        people = fused.people,
                        vehicles = fused.vehicles,
                        noiseDba = SensorBus.noise.value.leqDba,
                        downMbps = SensorBus.net.value.download.mbps,
                        pingMs = SensorBus.net.value.ping.avgMs,
                        wifiAp = SensorBus.wifi.value.apCount,
                        bleDevices = SensorBus.ble.value.entityCount
                    ),
                    max = s.historyWindow
                )
            }

            updateNotification(fused.people, fused.vehicles)
            delay(s.fusionIntervalMs)
        }
    }

    private suspend fun wifiScanLoop() {
        while (scope.isActive) {
            val s = app.settings.current
            if (s.mode.usesWifi) wifiScanner.requestScan()
            delay(s.wifiScanIntervalMs)
        }
    }

    private suspend fun pingLoop() {
        while (scope.isActive) {
            val s = app.settings.current
            val result = networkProbe.ping(s.pingHost, s.pingCount)
            mergeNet { copy(ping = result, updatedMs = System.currentTimeMillis()) }
            result.avgMs?.let { SensorBus.addPing(it) }
            delay(s.pingIntervalMs)
        }
    }

    private suspend fun throughputLoop() {
        delay(4_000)
        while (scope.isActive) {
            val s = app.settings.current
            val down = networkProbe.download(s.speedTestUrl, maxMs = 10_000L)
            mergeNet { copy(download = down, updatedMs = System.currentTimeMillis()) }
            down.mbps?.let { SensorBus.addDownload(it) }
            if (s.runUploadTest) {
                val up = networkProbe.upload(s.uploadUrl, s.uploadBytes)
                mergeNet { copy(upload = up) }
            }
            delay(s.speedTestIntervalMs)
        }
    }

    private suspend fun persistLoop() {
        while (scope.isActive) {
            delay(app.settings.current.persistIntervalMs)
            val fix = lastFix ?: continue
            val now = System.currentTimeMillis()
            val wifi = SensorBus.wifi.value
            val ble = SensorBus.ble.value
            val noise = SensorBus.noise.value
            val net = SensorBus.net.value
            val fused = SensorBus.fusion.value
            val sample = SampleEntity(
                t = now,
                lat = fix.lat,
                lon = fix.lon,
                accuracyM = fix.accuracyM,
                people = fused.people,
                vehicles = fused.vehicles,
                peopleRules = fused.peopleRaw,
                vehicleRules = fused.vehiclesRaw,
                confidence = fused.confidence,
                wifiApCount = wifi.apCount,
                wifiCvMedian = wifi.cvMedian,
                wifiDipRate = wifi.dipRatePerMin,
                wifiChurn = wifi.churnPerMin,
                bleEntities = ble.entityCount,
                bleNearby = ble.nearCount,
                blePedestrian = ble.pedestrianCount,
                bleVehicle = ble.vehicleCount,
                bleStatic = ble.staticCount,
                noiseDba = noise.leqDba,
                downMbps = net.download.mbps,
                upMbps = net.upload.mbps,
                pingMs = net.ping.avgMs,
                jitterMs = net.ping.jitterMs,
                lossPct = net.ping.lossPct,
                netType = net.networkType,
                rsrpDbm = net.cell.rsrpDbm
            )
            runCatching { withContext(Dispatchers.IO) { dao.insertSample(sample) } }
        }
    }

    private fun mergeNet(block: NetworkMetrics.() -> NetworkMetrics) {
        val base = networkProbe.snapshotBase()
        val current = SensorBus.net.value
        // Keep the accumulated ping/throughput results, refresh everything else.
        SensorBus.setNet(
            block(
                base.copy(
                    ping = current.ping,
                    download = current.download,
                    upload = current.upload
                )
            )
        )
    }

    // ----------------------------------------------------------------- location

    private val locationListener = LocationListener { loc -> onLocation(loc) }

    private fun startLocation() {
        if (!hasLocationPermission()) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm
        val thread = HandlerThread("ambient-location").also { it.start() }
        locationThread = thread
        val looper = thread.looper
        listOf(
            LocationManager.GPS_PROVIDER to 1_000L,
            LocationManager.NETWORK_PROVIDER to 5_000L,
            LocationManager.PASSIVE_PROVIDER to 10_000L
        ).forEach { (provider, minTime) ->
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, minTime, 0.5f, locationListener, looper)
                }
            }
        }
        // seed with the last known fix so the map is not empty at start
        runCatching {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            val best = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (best != null) onLocation(best)
        }
    }

    private fun stopLocation() {
        runCatching { locationManager?.removeUpdates(locationListener) }
        locationManager = null
        locationThread?.quitSafely()
        locationThread = null
    }

    @Suppress("DEPRECATION")
    private fun onLocation(loc: Location) {
        val fix = GeoFix(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else 100f,
            altitudeM = if (loc.hasAltitude()) loc.altitude else null,
            speedMps = if (loc.hasSpeed()) loc.speed else null,
            bearingDeg = if (loc.hasBearing()) loc.bearing else null,
            provider = loc.provider ?: "?",
            atMs = loc.time
        )
        lastFix = fix
        SensorBus.setLocation(fix)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sensing_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.sensing_channel_desc)
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafely() {
        val notification = buildNotification("—", "—")
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
        }.onFailure {
            // Fall back to the plain call; some OEMs reject the typed variant.
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun stopForegroundSafely() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    private var lastNotificationMs = 0L

    private fun updateNotification(people: Float, vehicles: Float) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationMs < 3_000L) return
        lastNotificationMs = now
        val body = "People ${formatCount(people)} · Vehicles ${formatCount(vehicles)}"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        runCatching { manager?.notify(NOTIFICATION_ID, buildNotification(body, "")) }
    }

    private fun buildNotification(people: String, vehicles: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentIntent = PendingIntent.getActivity(this, 0, intent, flags)
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, SensingService::class.java).setAction(ACTION_STOP), flags
        )
        val text = if (vehicles.isBlank()) people else "$people · $vehicles"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sensing_notification_title))
            .setContentText(text.ifBlank { getString(R.string.sensing_notification_text) })
            .setSmallIcon(R.drawable.ic_stat_sense)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.stop),
                stopIntent
            )
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ambientsense:session").apply {
            acquire(6 * 60 * 60 * 1000L) // safety cap; released when the session ends
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val CHANNEL_ID = "ambient_sensing"
        private const val NOTIFICATION_ID = 4271
        const val ACTION_STOP = "com.ambientsense.app.STOP"

        fun start(context: Context) {
            val intent = Intent(context, SensingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SensingService::class.java))
        }
    }
}
