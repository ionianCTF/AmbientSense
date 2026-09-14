package com.ambientsense.app.sensing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.ambientsense.app.model.CellSnapshot
import com.ambientsense.app.model.NetworkMetrics
import com.ambientsense.app.model.PingResult
import com.ambientsense.app.model.ThroughputResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * Connectivity probing: transport, generation, radio measurements, ICMP latency and
 * TCP throughput.
 *
 * Everything that can throw a SecurityException (cell info, Wi-Fi SSID, data network
 * type) is guarded, because OEMs differ in what they hand out to third party apps:
 * if a field is unavailable the UI shows a dash rather than crashing the session.
 */
class NetworkProbe(context: Context) {

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @Volatile var displayInfo: TelephonyDisplayInfo? = null
        private set
    @Volatile var lastCells: List<CellInfo> = emptyList()
        private set

    private var telephonyCallback: TelephonyCallback? = null

    var hasPhonePermission: Boolean = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED
        private set

    // ------------------------------------------------------------- monitoring

    @SuppressLint("MissingPermission")
    fun startMonitoring() {
        refreshCellInfo()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasPhonePermission) {
            runCatching {
                val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                        displayInfo = info
                    }
                }
                tm.registerTelephonyCallback(appContext.mainExecutor, cb)
                telephonyCallback = cb
            }
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                telephonyCallback?.let { tm.unregisterTelephonyCallback(it) }
            }
        }
        telephonyCallback = null
    }

    @SuppressLint("MissingPermission")
    fun refreshCellInfo() {
        if (!hasPhonePermission && ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            val cells = tm.allCellInfo
            if (!cells.isNullOrEmpty()) lastCells = cells
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tm.requestCellInfoUpdate(appContext.mainExecutor, object :
                    TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cells: MutableList<CellInfo>) {
                        if (cells.isNotEmpty()) lastCells = cells
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) = Unit
                })
            }
        }
    }

    // -------------------------------------------------------------- snapshot

    @SuppressLint("MissingPermission")
    fun snapshotBase(nowMs: Long = System.currentTimeMillis()): NetworkMetrics {
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi‑Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "Bluetooth tether"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
            else -> "Offline"
        }

        val wifi = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            readWifiInfo(caps)
        } else null

        val cell = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            readCell()
        } else CellSnapshot()

        return NetworkMetrics(
            transport = transport,
            networkType = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                generationLabel() else (wifi?.generation ?: "—"),
            isMetered = runCatching { cm.isActiveNetworkMetered }.getOrDefault(false),
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            linkDownMbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.div(1000),
            linkUpMbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 }?.div(1000),
            wifiSsid = wifi?.ssid,
            wifiRssiDbm = wifi?.rssi,
            wifiLinkMbps = wifi?.linkMbps,
            wifiFreqMhz = wifi?.freqMhz,
            wifiStandard = wifi?.standard,
            cell = cell,
            updatedMs = nowMs
        )
    }

    private data class WifiBits(
        val ssid: String?,
        val rssi: Int?,
        val standard: String?,
        val linkMbps: Int?,
        val freqMhz: Int?,
        val generation: String
    )

    @SuppressLint("MissingPermission")
    private fun readWifiInfo(caps: NetworkCapabilities): WifiBits? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            caps.transportInfo as? WifiInfo
        } else {
            runCatching { wifiManager?.connectionInfo }.getOrNull()
        } ?: return null

        val ssid = runCatching {
            info.ssid?.trim('"')
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
        }.getOrNull()

        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (info.wifiStandard) {
                ScanResult.WIFI_STANDARD_11BE -> "Wi‑Fi 7"
                ScanResult.WIFI_STANDARD_11AX -> "Wi‑Fi 6"
                ScanResult.WIFI_STANDARD_11AC -> "Wi‑Fi 5"
                ScanResult.WIFI_STANDARD_11N -> "Wi‑Fi 4"
                ScanResult.WIFI_STANDARD_LEGACY -> "802.11a/b/g"
                else -> null
            }
        } else null

        val link = info.linkSpeed
        val gen = standard ?: "Wi‑Fi"
        return WifiBits(ssid, info.rssi, standard, link, info.frequency, gen)
    }

    private fun readCell(): CellSnapshot {
        val cells = lastCells
        val registered = cells.firstOrNull { it.isRegistered } ?: return CellSnapshot(
            operator = tm.networkOperatorName?.takeIf { it.isNotBlank() },
            generation = generationLabel(),
            detailedTech = "No cell info (permission or API)",
            neighborCells = cells.size
        )

        val operator = tm.networkOperatorName?.takeIf { it.isNotBlank() }
        val neighbors = cells.count { !it.isRegistered }

        return when (registered) {
            is CellInfoLte -> {
                val id = registered.cellIdentity
                val sig = registered.cellSignalStrength
                CellSnapshot(
                    operator = operator,
                    generation = generationLabel(),
                    detailedTech = "LTE",
                    band = id.earfcn.takeIf { it in 0..262143 }?.let { lteBandFromEarfcn(it) },
                    cellId = id.ci.takeIf { it != Int.MAX_VALUE }?.toString(16)?.let { "eNB/CI $it" },
                    rsrpDbm = sig.rsrp.takeIf { it != CellInfo.UNAVAILABLE },
                    rsrqDb = sig.rsrq.takeIf { it != CellInfo.UNAVAILABLE },
                    sinrDb = sig.rssnr.takeIf { it != Int.MAX_VALUE },
                    timingAdvance = sig.timingAdvance.takeIf { it != CellInfo.UNAVAILABLE },
                    neighborCells = neighbors
                )
            }
            is CellInfoNr -> {
                // Since Android 16 CellInfoNr exposes the base CellIdentity/CellSignalStrength
                // types, so the 5G-specific values need a downcast.
                val id = registered.cellIdentity as? CellIdentityNr
                val sig = registered.cellSignalStrength as? CellSignalStrengthNr
                CellSnapshot(
                    operator = operator,
                    generation = generationLabel(),
                    detailedTech = "NR (5G)",
                    band = id?.nrarfcn?.takeIf { it in 0..3_279_165 }?.let { nrBandFromArfcn(it) },
                    cellId = id?.nci?.takeIf { it != Long.MAX_VALUE }?.toString(16)?.let { "NCI $it" },
                    rsrpDbm = sig?.ssRsrp?.takeIf { it != CellInfo.UNAVAILABLE },
                    rsrqDb = sig?.ssRsrq?.takeIf { it != CellInfo.UNAVAILABLE },
                    sinrDb = sig?.ssSinr?.takeIf { it != CellInfo.UNAVAILABLE },
                    neighborCells = neighbors
                )
            }
            is CellInfoWcdma -> {
                val sig = registered.cellSignalStrength
                CellSnapshot(
                    operator = operator,
                    generation = "3G",
                    detailedTech = "UMTS/HSPA",
                    rsrpDbm = sig.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                    neighborCells = neighbors
                )
            }
            is CellInfoGsm -> {
                val sig = registered.cellSignalStrength
                CellSnapshot(
                    operator = operator,
                    generation = "2G",
                    detailedTech = "GSM/EDGE",
                    rsrpDbm = sig.dbm.takeIf { it != CellInfo.UNAVAILABLE },
                    neighborCells = neighbors
                )
            }
            else -> CellSnapshot(operator = operator, generation = generationLabel(), neighborCells = neighbors)
        }
    }

    @SuppressLint("MissingPermission")
    private fun generationLabel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayInfo?.let { di ->
                val o = di.overrideNetworkType
                val label = when (o) {
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> "5G NSA mmWave"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G Advanced"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4G Advanced Pro"
                    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "4G Carrier Aggregation"
                    else -> null
                }
                if (label != null) return label
            }
        }
        return runCatching {
            when (tm.dataNetworkType) {
                // No NSA override reported while the data network is NR => standalone
                TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "Cellular"
            }
        }.getOrDefault("Cellular")
    }

    // ----------------------------------------------------------------- ping

    suspend fun ping(host: String, count: Int): PingResult = withContext(Dispatchers.IO) {
        val shell = tryShellPing(host, count)
        if (shell != null && shell.packetsReceived > 0) return@withContext shell
        // Fallback: TCP handshake RTT (works when ICMP is filtered or ping is absent)
        return@withContext tcpPing(host, count)
    }

    private fun tryShellPing(host: String, count: Int): PingResult? {
        val binary = listOf("/system/bin/ping", "/system/xbin/ping", "/bin/ping").firstOrNull {
            java.io.File(it).exists()
        } ?: return null
        return runCatching {
            val proc = ProcessBuilder()
                .command(binary, "-c", count.toString(), "-W", "2", host)
                .redirectErrorStream(true)
                .start()
            val text = proc.inputStream.bufferedReader().use(BufferedReader::readText)
            proc.waitFor(count + 4L, TimeUnit.SECONDS)
            parsePing(host, text)
        }.getOrNull()
    }

    private fun parsePing(host: String, text: String): PingResult {
        val times = ArrayList<Float>()
        val timeRegex = Regex("time[=<]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms")
        timeRegex.findAll(text).forEach { times.add(it.groupValues[1].toFloat()) }

        val lossRegex = Regex("([0-9]+(?:\\.[0-9]+)?)% packet loss")
        val loss = lossRegex.find(text)?.groupValues?.get(1)?.toFloatOrNull()

        val txRegex = Regex("(\\d+) packets transmitted,\\s*(\\d+) received")
        val m = txRegex.find(text)
        val sent = m?.groupValues?.get(1)?.toIntOrNull() ?: times.size
        val received = m?.groupValues?.get(2)?.toIntOrNull() ?: times.size

        if (times.isEmpty()) return PingResult(host = host, lossPct = loss ?: 100f, atMs = System.currentTimeMillis())

        val min = times.min()
        val max = times.max()
        val avg = times.average().toFloat()
        // RFC 3550 style mean deviation — the standard "jitter" figure
        var jitter = 0f
        for (i in 1 until times.size) {
            jitter += kotlin.math.abs(times[i] - times[i - 1])
        }
        jitter = if (times.size > 1) jitter / (times.size - 1) else 0f
        return PingResult(
            host = host,
            minMs = min,
            avgMs = avg,
            maxMs = max,
            jitterMs = jitter,
            lossPct = loss ?: (100f * (1f - received.toFloat() / sent.toFloat().coerceAtLeast(1f))),
            packetsSent = sent,
            packetsReceived = received,
            atMs = System.currentTimeMillis()
        )
    }

    private fun tcpPing(host: String, count: Int): PingResult {
        val times = ArrayList<Float>()
        repeat(count) {
            val t0 = System.nanoTime()
            val ok = runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, 443), 2000)
                    true
                }
            }.getOrDefault(false)
            if (ok) times.add((System.nanoTime() - t0) / 1e6f)
            if (times.size < count) runCatching { Thread.sleep(120) }
        }
        if (times.isEmpty()) {
            return PingResult(host = host, lossPct = 100f, packetsSent = count, atMs = System.currentTimeMillis())
        }
        var jitter = 0f
        for (i in 1 until times.size) jitter += kotlin.math.abs(times[i] - times[i - 1])
        jitter = if (times.size > 1) jitter / (times.size - 1) else 0f
        return PingResult(
            host = host,
            minMs = times.min(),
            avgMs = times.average().toFloat(),
            maxMs = times.max(),
            jitterMs = jitter,
            lossPct = 100f * (1f - times.size.toFloat() / count),
            packetsSent = count,
            packetsReceived = times.size,
            viaTcp = true,
            atMs = System.currentTimeMillis()
        )
    }

    // ----------------------------------------------------------- throughput

    suspend fun download(
        url: String,
        maxMs: Long = 10_000L,
        maxBytes: Long = 40_000_000L
    ): ThroughputResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 5_000
            c.readTimeout = 8_000
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("User-Agent", "AmbientSense/0.9")
            c.instanceFollowRedirects = true
            c.connect()
            val code = c.responseCode
            if (code >= 400) return@withContext ThroughputResult(error = "HTTP $code", atMs = System.currentTimeMillis())

            val buf = ByteArray(65_536)
            var total = 0L
            val t0 = System.nanoTime()
            c.inputStream.use { stream ->
                while (total < maxBytes) {
                    val r = stream.read(buf)
                    if (r <= 0) break
                    total += r
                    if ((System.nanoTime() - t0) / 1_000_000L > maxMs) break
                }
            }
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000L
            val mbps = if (elapsedMs > 0) (total * 8.0) / (elapsedMs / 1000.0) / 1_000_000.0 else null
            ThroughputResult(
                mbps = mbps?.toFloat(),
                bytes = total,
                durationMs = elapsedMs,
                atMs = System.currentTimeMillis()
            )
        } catch (t: Throwable) {
            ThroughputResult(error = t.message ?: "Download failed", atMs = System.currentTimeMillis())
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun upload(url: String, bytes: Int): ThroughputResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 5_000
            c.readTimeout = 10_000
            c.setFixedLengthStreamingMode(bytes)
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setRequestProperty("User-Agent", "AmbientSense/0.9")
            c.connect()
            val payload = ByteArray(65_536) { (it % 251).toByte() }
            var remaining = bytes
            val t0 = System.nanoTime()
            c.outputStream.use { out ->
                while (remaining > 0) {
                    val chunk = kotlin.math.min(payload.size, remaining)
                    out.write(payload, 0, chunk)
                    remaining -= chunk
                }
                out.flush()
            }
            val code = c.responseCode
            // Drain the response so the socket is reusable / the server acknowledges
            runCatching { c.inputStream.readBytes() }
            val elapsedMs = (System.nanoTime() - t0) / 1_000_000L
            val mbps = if (elapsedMs > 0) (bytes * 8.0) / (elapsedMs / 1000.0) / 1_000_000.0 else null
            ThroughputResult(
                mbps = mbps?.toFloat(),
                bytes = bytes.toLong(),
                durationMs = elapsedMs,
                atMs = System.currentTimeMillis(),
                error = if (code >= 400) "HTTP $code" else null
            )
        } catch (t: Throwable) {
            ThroughputResult(error = t.message ?: "Upload failed", atMs = System.currentTimeMillis())
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        /** Mapping of EARFCN to the LTE band it belongs to (3GPP 36.101). */
        fun lteBandFromEarfcn(earfcn: Int): String? = when (earfcn) {
            in 0..599 -> "B1"
            in 600..1199 -> "B2"
            in 1200..1949 -> "B3"
            in 1950..2399 -> "B4"
            in 2400..2649 -> "B5"
            in 2650..2749 -> "B6"
            in 2750..3449 -> "B7"
            in 3450..3799 -> "B8"
            in 3800..4149 -> "B9"
            in 4150..4749 -> "B10"
            in 4750..4949 -> "B11"
            in 5010..5179 -> "B12"
            in 5180..5279 -> "B13"
            in 5280..5379 -> "B14"
            in 5730..5849 -> "B17"
            in 5850..5999 -> "B18"
            in 6000..6149 -> "B19"
            in 6150..6449 -> "B20"
            in 6450..6599 -> "B21"
            in 6600..7399 -> "B22"
            in 7500..7699 -> "B23"
            in 7700..8039 -> "B24"
            in 8040..8689 -> "B25"
            in 8690..9039 -> "B26"
            in 9040..9209 -> "B28"
            in 9210..9659 -> "B30"
            in 9660..9769 -> "B31"
            in 9770..9869 -> "B32"
            in 36200..36349 -> "B33"
            in 9870..9919 -> "B34"
            in 36350..36949 -> "B35"
            in 36950..37549 -> "B36"
            in 37550..37749 -> "B37"
            in 37750..38249 -> "B38"
            in 38250..38649 -> "B39"
            in 38650..39649 -> "B40"
            in 39650..41589 -> "B41"
            in 41590..43589 -> "B42"
            in 43590..45589 -> "B43"
            in 45590..46589 -> "B44"
            in 46590..46789 -> "B45"
            in 46790..54539 -> "B46"
            in 54540..55239 -> "B47"
            in 55240..56739 -> "B48"
            in 56740..58239 -> "B49"
            in 58240..59089 -> "B50"
            in 59090..59139 -> "B51"
            in 59140..60139 -> "B52"
            in 60140..60254 -> "B53"
            in 65536..66435 -> "B65"
            in 66436..67335 -> "B66"
            in 67336..67535 -> "B67"
            in 67536..67835 -> "B68"
            in 67836..68335 -> "B69"
            in 68336..68585 -> "B70"
            in 68586..68935 -> "B71"
            in 68936..68985 -> "B72"
            in 68986..69035 -> "B73"
            in 69036..69465 -> "B74"
            in 69466..70315 -> "B75"
            in 70316..70365 -> "B76"
            in 70366..70545 -> "B85"
            in 70546..70695 -> "B87"
            in 70696..70795 -> "B88"
            else -> null
        }

        /** Mapping of NR-ARFCN to the 5G band it belongs to (3GPP 38.101-1/2). */
        fun nrBandFromArfcn(arfcn: Int): String? = when (arfcn) {
            in 422000..434000 -> "n1"
            in 386000..398000 -> "n2 / n25"
            in 361000..376000 -> "n3"
            in 173800..178800 -> "n5"
            in 524000..538000 -> "n7"
            in 185000..192000 -> "n8"
            in 145800..149200 -> "n12"
            in 151600..153600 -> "n13"
            in 158200..164200 -> "n20"
            in 376000..384000 -> "n23"
            in 386000..399000 -> "n25"
            in 399000..404000 -> "n26"
            in 171800..178800 -> "n28"
            in 470000..472000 -> "n30"
            in 402000..405000 -> "n34"
            in 514000..524000 -> "n38"
            in 376000..384000 -> "n39"
            in 460000..480000 -> "n40"
            in 499200..538000 -> "n41"
            in 743334..795000 -> "n46"
            in 636667..646666 -> "n47"
            in 552400..567400 -> "n48"
            in 143400..145600 -> "n50"
            in 285400..286400 -> "n51"
            in 496700..499000 -> "n53"
            in 422000..440000 -> "n65"
            in 422000..441000 -> "n66"
            in 399000..404000 -> "n70"
            in 123400..130400 -> "n71"
            in 295000..303600 -> "n74"
            in 286400..303400 -> "n75"
            in 285400..295000 -> "n76"
            in 620000..680000 -> "n77"
            in 620000..653333 -> "n78"
            in 693334..733333 -> "n79"
            in 2054166..2104165 -> "n257 (28 GHz)"
            in 2016667..2070832 -> "n258 (26 GHz)"
            in 2229166..2287499 -> "n260 (39 GHz)"
            in 2070833..2084999 -> "n261 (28 GHz)"
            else -> null
        }

        /** Rough quality word for an LTE/NR RSRP reading. */
        fun rsrpQuality(rsrp: Int?): String = when {
            rsrp == null -> "—"
            rsrp >= -80 -> "Excellent"
            rsrp >= -90 -> "Good"
            rsrp >= -100 -> "Fair"
            rsrp >= -110 -> "Weak"
            else -> "Very weak"
        }

        /** Standard deviation helper used by the latency chart. */
        fun stdDev(values: List<Float>): Float {
            if (values.size < 2) return 0f
            val m = values.average().toFloat()
            val v = values.sumOf { ((it - m) * (it - m)).toDouble() } / (values.size - 1)
            return sqrt(v).toFloat()
        }
    }
}
