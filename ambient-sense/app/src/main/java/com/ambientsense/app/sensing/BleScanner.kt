package com.ambientsense.app.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.model.BleMetrics

/**
 * Continuous BLE observer feeding [DeviceTracker].
 *
 * Notes on platform reality:
 *  - Android throttles scans hard when the app is backgrounded; the scanner is driven
 *    from a foreground service with a visible notification, which is the only reliable
 *    way to keep a high duty cycle.
 *  - Since Android 6 most phones advertise with **randomised** MAC addresses that
 *    rotate every ~15 minutes. We detect the locally-administered bit and flag such
 *    tracks, because their short lifetime inflates naive device counts.
 *  - Advertisement payloads (name, TX power, manufacturer id) are read from the scan
 *    record rather than from BluetoothDevice, which keeps us clear of the
 *    BLUETOOTH_CONNECT permission.
 */
class BleScanner(
    private val context: Context,
    private val settings: () -> SenseSettings
) {

    private val tracker = DeviceTracker(settings)
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var running = false

    fun hasPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED
    }

    fun bluetoothEnabled(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) return false
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter = bm.adapter ?: return false
        if (!adapter.isEnabled) return false
        val sc = adapter.bluetoothLeScanner ?: return false
        scanner = sc

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                ingest(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::ingest)
            }

            override fun onScanFailed(errorCode: Int) {
                lastError = "BLE scan failed ($errorCode)"
            }
        }
        callback = cb

        val s = settings()
        val builder = ScanSettings.Builder()
            .setScanMode(s.bleScanMode)
            .setReportDelay(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
        }
        // Only deliver one result per device per scan window to cut duplicate work.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            builder.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
        }
        return runCatching {
            sc.startScan(null, builder.build(), cb)
            running = true
            lastError = null
            true
        }.getOrElse {
            lastError = it.message
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching {
            callback?.let { scanner?.stopScan(it) }
        }
        callback = null
        scanner = null
        running = false
    }

    fun reset() = tracker.reset()

    /** Called on the fusion tick: expires tracks and returns the snapshot. */
    fun tick(nowMs: Long): BleMetrics = tracker.tick(nowMs)

    var lastError: String? = null
        private set

    private fun ingest(result: ScanResult) {
        val now = System.currentTimeMillis()
        val address = result.device?.address ?: return
        val record = result.scanRecord
        val tx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            record?.txPowerLevel ?: Int.MIN_VALUE
        } else Int.MIN_VALUE
        val manufacturer = try {
            val data = record?.manufacturerSpecificData
            if (data != null && data.size() > 0) data.keyAt(0) else -1
        } catch (_: Exception) {
            -1
        }
        tracker.onSighting(
            key = address,
            name = record?.deviceName,
            rssi = result.rssi,
            txPower = tx,
            manufacturerId = manufacturer,
            randomizedMac = isRandomizedMac(address),
            nowMs = now
        )
    }

    companion object {
        /**
         * True when the MAC carries the locally-administered bit, i.e. the phone is
         * rotating a random address instead of burning in its hardware one.
         */
        fun isRandomizedMac(address: String): Boolean {
            val first = address.substringBefore(':')
            if (first.length != 2) return false
            val b = first.toIntOrNull(16) ?: return false
            return (b and 0x02) != 0
        }
    }
}
