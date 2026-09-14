package com.ambientsense.app.sensing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.model.NoiseMetrics
import com.ambientsense.app.util.Fft
import com.ambientsense.app.util.ema
import kotlin.math.log10
import kotlin.math.max

/**
 * Sound level meter.
 *
 * The microphone delivers raw PCM; turning that into something a human would call
 * "dB(A)" takes three steps:
 *
 *  1. Hann-windowed FFT of each block,
 *  2. apply the IEC 61672-1 A-weighting curve per bin and sum weighted energy,
 *     with Parseval's theorem to get back to a time-domain mean square
 *     (including the 8/3 coherent-gain correction for the Hann window),
 *  3. convert to dB relative to full scale and add a user calibration offset that
 *     maps dBFS to dB SPL for this particular phone's microphone.
 *
 * The offset defaults to 94 dB, which is the usual ballpark for a phone mic; the
 * calibration screen lets the user trim it against a reference.
 *
 * Note: Android blocks microphone capture while the app is in the background, so the
 * acoustic channel only produces data while Ambient Sense is on screen.
 */
class NoiseMeter(
    private val context: Context,
    private val settings: () -> SenseSettings
) {

    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null

    private var accLinear = 0.0
    private var accCount = 0
    private var peakLinear = 0.0
    private var slowDb = -120f
    private var sessionMaxDb = -120f
    private val perSecond = ArrayDeque<Float>(70)
    private var lastSecondFlushMs = 0L
    private var floorDb: Float? = null

    var lastError: String? = null
        private set

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) {
            lastError = "Microphone permission missing"
            return false
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            lastError = "Audio hardware unavailable"
            return false
        }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf * 2, FRAME * 4)
            )
        } catch (t: Throwable) {
            lastError = t.message ?: "AudioRecord init failed"
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            lastError = "Microphone busy or unavailable"
            return false
        }
        recorder = rec
        running = true
        lastError = null

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val frame = ShortArray(FRAME)
            val re = DoubleArray(FRAME)
            val im = DoubleArray(FRAME)
            val window = Fft.hann(FRAME)
            val weights = DoubleArray(FRAME / 2 + 1) { k ->
                Fft.aWeightingLinear(k * SAMPLE_RATE.toDouble() / FRAME)
            }
            try {
                rec.startRecording()
                while (running) {
                    val read = rec.read(frame, 0, FRAME)
                    if (read <= 0) continue
                    val ms = analyse(frame, read, re, im, window, weights)
                    accLinear += ms
                    accCount++
                    if (ms > peakLinear) peakLinear = ms
                    val db = levelToDb(ms)
                    slowDb = ema(slowDb.coerceAtLeast(-120f), db, 0.12f)
                    if (db > sessionMaxDb) sessionMaxDb = db
                    flushSecondIfNeeded()
                }
            } catch (t: Throwable) {
                lastError = t.message
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
            }
        }, "ambient-noise").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        thread?.interrupt()
        thread = null
    }

    fun reset() {
        accLinear = 0.0
        accCount = 0
        peakLinear = 0.0
        slowDb = -120f
        sessionMaxDb = -120f
        perSecond.clear()
        floorDb = null
    }

    /** Publishes the averaged level since the last call. */
    fun sample(nowMs: Long): NoiseMetrics {
        if (!running) {
            return NoiseMetrics(message = lastError ?: "Microphone idle", running = false)
        }
        val meanLinear = if (accCount > 0) accLinear / accCount else 0.0
        accLinear = 0.0
        accCount = 0
        val leq = levelToDb(meanLinear)
        val peak = levelToDb(peakLinear)
        peakLinear = 0.0

        while (perSecond.size > 60) perSecond.removeFirst()
        floorDb = if (perSecond.isNotEmpty()) perSecond.minOrNull() else null

        return NoiseMetrics(
            leqDba = leq,
            slowDba = slowDb,
            peakDba = peak,
            floorDba = floorDb,
            maxDba = if (sessionMaxDb > -119f) sessionMaxDb else null,
            updatedMs = nowMs,
            running = true,
            message = lastError
        )
    }

    private fun flushSecondIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastSecondFlushMs < 1_000L) return
        lastSecondFlushMs = now
        perSecond.addLast(slowDb)
        while (perSecond.size > 60) perSecond.removeFirst()
    }

    /**
     * A-weighted mean square (normalised to full scale) for one block.
     */
    private fun analyse(
        frame: ShortArray,
        count: Int,
        re: DoubleArray,
        im: DoubleArray,
        window: DoubleArray,
        weights: DoubleArray
    ): Double {
        for (i in 0 until FRAME) {
            val v = if (i < count) frame[i].toDouble() / 32768.0 else 0.0
            re[i] = v * window[i]
            im[i] = 0.0
        }
        Fft.forward(re, im)
        val n = FRAME
        val half = n / 2
        var energy = 0.0
        for (k in 1 until half) {
            val mag2 = re[k] * re[k] + im[k] * im[k]
            energy += weights[k] * mag2
        }
        // Parseval (single sided) + Hann coherent-gain correction
        var meanSquare = 2.0 * energy / (n.toDouble() * n.toDouble())
        meanSquare *= 8.0 / 3.0
        return if (meanSquare < 1e-14) 1e-14 else meanSquare
    }

    private fun levelToDb(meanSquare: Double): Float =
        (10.0 * log10(max(meanSquare, 1e-14)) + settings().micCalibrationDb).toFloat()

    companion object {
        private const val SAMPLE_RATE = 44_100
        private const val FRAME = 2048
    }
}
