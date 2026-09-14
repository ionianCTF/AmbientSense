package com.ambientsense.app.sensing

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Online recursive-least-squares (RLS) model with a forgetting factor.
 *
 * The model does **not** try to learn the absolute crowd size from scratch — that
 * would need hundreds of labels. Instead it learns the *correction* to the physics
 * heuristic:
 *
 *      estimate = heuristic(features) + model.predict(features)
 *
 * so with zero labels the app behaves like the calibrated-rules estimator, and every
 * ground-truth label the user submits nudges the surface towards their environment,
 * their phone, and their habitual spots. The first feature is always the heuristic
 * value itself, which lets the model learn a simple scale/offset if that explains
 * the data better than anything else.
 *
 * All state is persisted, so calibration survives restarts.
 */
class CalibrationModel(
    private val prefs: SharedPreferences,
    private val storageKey: String,
    val featureNames: List<String>,
    private val forgetting: Double = 0.985,
    private val initialCovariance: Double = 5.0
) {

    private val nFeatures = featureNames.size
    private val n = nFeatures + 1               // + bias column
    private var w = DoubleArray(n)              // weights, index 0 = bias
    private var cov = DoubleArray(n * n)        // P matrix, row major
    private var featMean = DoubleArray(nFeatures)
    private var featM2 = DoubleArray(nFeatures)
    private var featCount = 0
    private var biasEma = 0.0
    private var absErrSum = 0.0
    private var sqErrSum = 0.0
    private var observations = 0
    /**
     * Labels that still count towards [trustWeight]. Decoupled from [observations]
     * because changing the sensing radius invalidates a calibration without making
     * the weights worthless: [decayTrust] drops the trust to zero, keeps the weights
     * as a warm start, and new labels at the new radius fade them out.
     */
    private var warmSamples = 0
    private var sumY = 0.0
    private var sumY2 = 0.0
    private var lastError: Double? = null

    /** Last z-scored feature vector, kept so the UI can show live contributions. */
    private var lastZ: DoubleArray = DoubleArray(n)

    init {
        restore()
    }

    // ------------------------------------------------------------------ inference

    /**
     * @param features raw feature values, same order as [featureNames]
     * @return predicted correction to add to the heuristic estimate
     */
    fun predict(features: DoubleArray): Double {
        if (observations == 0) return 0.0
        val z = standardize(features, updateStats = false)
        lastZ = z
        return dot(w, z)
    }

    /** Predicted correction, decomposed per feature for the explainability UI. */
    fun explain(features: DoubleArray): List<Pair<String, Float>> {
        if (observations == 0) return featureNames.map { it to 0f }
        val z = standardize(features, updateStats = false)
        return featureNames.mapIndexed { i, name ->
            name to (w[i + 1] * z[i + 1]).toFloat()
        }
    }

    // ------------------------------------------------------------------- training

    /**
     * Feed one labelled sample.
     * @param target residual = groundTruth - heuristicEstimate
     */
    fun update(features: DoubleArray, target: Double) {
        val z = standardize(features, updateStats = true)
        lastZ = z

        // px = P * x
        val px = DoubleArray(n)
        for (i in 0 until n) {
            var s = 0.0
            val base = i * n
            for (j in 0 until n) s += cov[base + j] * z[j]
            px[i] = s
        }

        // denom = lambda + x' P x
        var denom = forgetting
        for (i in 0 until n) denom += z[i] * px[i]
        if (abs(denom) < 1e-9) return

        // gain k = P x / denom
        val k = DoubleArray(n) { px[it] / denom }

        val prediction = dot(w, z)
        val error = target - prediction

        for (i in 0 until n) w[i] += k[i] * error

        // P = (P - k x' P) / lambda   (x' P == px' because P is symmetric)
        for (i in 0 until n) {
            val base = i * n
            for (j in 0 until n) {
                cov[base + j] = (cov[base + j] - k[i] * px[j]) / forgetting
            }
        }

        // Running quality metrics
        observations++
        warmSamples++
        absErrSum += abs(error)
        sqErrSum += error * error
        sumY += target
        sumY2 += target * target
        biasEma = if (observations == 1) error else biasEma + 0.25 * (error - biasEma)
        lastError = error
        persist()
    }

    // --------------------------------------------------------------------- stats

    data class Stats(
        val observations: Int,
        val mae: Double,
        val rmse: Double,
        val r2: Double?,
        val recentBias: Double,
        val lastError: Double?,
        val weights: List<Pair<String, Double>>
    )

    fun stats(): Stats {
        val mae = if (observations > 0) absErrSum / observations else 0.0
        val rmse = if (observations > 0) sqrt(sqErrSum / observations) else 0.0
        val r2 = if (observations > 2) {
            val meanY = sumY / observations
            val varY = (sumY2 / observations) - meanY * meanY
            if (varY > 1e-9) 1.0 - (sqErrSum / observations) / varY else null
        } else null
        return Stats(
            observations = observations,
            mae = mae,
            rmse = rmse,
            r2 = r2,
            recentBias = biasEma,
            lastError = lastError,
            weights = featureNames.mapIndexed { i, name -> name to w[i + 1] }
        )
    }

    val labelCount: Int get() = observations

    /** Labels the model is currently willing to act on. */
    val trustedLabelCount: Int get() = warmSamples

    /**
     * How much we trust the learned model relative to the rules, 0..1.
     *
     * Zero until [MIN_LABELS] labels have been taken *at the current setting*, then
     * ramping to 1 over [RAMP_LABELS] more.
     */
    fun trustWeight(): Float {
        if (warmSamples < MIN_LABELS) return 0f
        return ((warmSamples - MIN_LABELS).toFloat() / RAMP_LABELS).coerceIn(0f, 1f)
    }

    /**
     * Drop confidence in the current weights without discarding them. Used when the
     * user changes the sensing radius: measured in validation, a model transferred
     * across radii is *worse* than the plain rules estimate, so the honest reaction
     * is to fall back to physics and re-earn trust as labels arrive.
     */
    fun decayTrust() {
        warmSamples = 0
        persist()
    }

    fun reset() {
        w = DoubleArray(n)
        cov = DoubleArray(n * n) { i -> if (i % (n + 1) == 0) 1.0 / initialCovariance else 0.0 }
        featMean = DoubleArray(nFeatures)
        featM2 = DoubleArray(nFeatures)
        featCount = 0
        biasEma = 0.0
        absErrSum = 0.0
        sqErrSum = 0.0
        observations = 0
        warmSamples = 0
        sumY = 0.0
        sumY2 = 0.0
        lastError = null
        persist()
    }

    // ------------------------------------------------------------- standardisation

    private fun standardize(features: DoubleArray, updateStats: Boolean): DoubleArray {
        val z = DoubleArray(n)
        z[0] = 1.0
        for (i in 0 until nFeatures) {
            val raw = features.getOrElse(i) { 0.0 }
            if (updateStats) {
                // Welford
                featCount++
                val delta = raw - featMean[i]
                featMean[i] += delta / featCount
                featM2[i] += delta * (raw - featMean[i])
            }
            val sd = if (featCount > 1) sqrt(featM2[i] / (featCount - 1)) else 0.0
            val safeSd = if (sd > 1e-6) sd else 1.0
            z[i + 1] = ((raw - featMean[i]) / safeSd).coerceIn(-8.0, 8.0)
        }
        return z
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in 0 until n) s += a[i] * b[i]
        return s
    }

    // ---------------------------------------------------------------- persistence

    private fun persist() {
        val sb = StringBuilder()
        sb.append(VERSION).append(';')
        sb.append(w.joinToString(",")).append(';')
        sb.append(cov.joinToString(",")).append(';')
        sb.append(featMean.joinToString(",")).append(';')
        sb.append(featM2.joinToString(",")).append(';')
        sb.append("$featCount;$biasEma;$absErrSum;$sqErrSum;$observations;$sumY;$sumY2;$warmSamples")
        prefs.edit().putString(storageKey, sb.toString()).apply()
    }

    private fun restore() {
        w = DoubleArray(n)
        cov = DoubleArray(n * n) { i -> if (i % (n + 1) == 0) 1.0 / initialCovariance else 0.0 }
        val blob = prefs.getString(storageKey, null) ?: return
        runCatching {
            val parts = blob.split(';')
            if (parts.size < 7 || parts[0] != VERSION) return
            w = parseDoubles(parts[1], n)
            cov = parseDoubles(parts[2], n * n)
            featMean = parseDoubles(parts[3], nFeatures)
            featM2 = parseDoubles(parts[4], nFeatures)
            featCount = parts[5].toInt()
            val tail = parts[6].split(',')
            biasEma = tail[0].toDouble()
            absErrSum = tail[1].toDouble()
            sqErrSum = tail[2].toDouble()
            observations = tail[3].toInt()
            sumY = tail[4].toDouble()
            sumY2 = tail[5].toDouble()
            warmSamples = tail.getOrNull(6)?.toIntOrNull() ?: observations
        }
    }

    private fun parseDoubles(s: String, expected: Int): DoubleArray {
        if (s.isBlank()) return DoubleArray(expected)
        val arr = s.split(',').map { it.toDoubleOrNull() ?: 0.0 }
        return DoubleArray(expected) { arr.getOrElse(it) { 0.0 } }
    }

    companion object {
        const val VERSION = "v2"
        const val MIN_LABELS = 6
        const val RAMP_LABELS = 24f
    }
}
