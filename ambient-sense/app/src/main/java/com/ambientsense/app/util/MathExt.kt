package com.ambientsense.app.util

import kotlin.math.pow
import kotlin.math.sqrt

/** Small numeric helpers shared by the sensing pipeline. */

fun Float.clampIn(min: Float, max: Float): Float =
    if (this < min) min else if (this > max) max else this

fun Double.clampIn(min: Double, max: Double): Double =
    if (this < min) min else if (this > max) max else this

fun Float.lerpTo(other: Float, t: Float): Float = this + (other - this) * t

/** Exponential moving average step. */
fun ema(current: Float, sample: Float, alpha: Float): Float =
    current + alpha * (sample - current)

fun DoubleArray.meanOf(n: Int = size): Double {
    if (n == 0) return 0.0
    var s = 0.0
    for (i in 0 until n) s += this[i]
    return s / n
}

/** Sample standard deviation (n-1). */
fun DoubleArray.stdOf(n: Int = size): Double {
    if (n < 2) return 0.0
    val m = meanOf(n)
    var s = 0.0
    for (i in 0 until n) {
        val d = this[i] - m
        s += d * d
    }
    return sqrt(s / (n - 1))
}

/** Linear-interpolated percentile of an already sorted array (p in 0..1). */
fun percentileSorted(sorted: DoubleArray, p: Double): Double {
    if (sorted.isEmpty()) return 0.0
    if (sorted.size == 1) return sorted[0]
    val idx = (p * (sorted.size - 1)).toDouble()
    val lo = kotlin.math.floor(idx).toInt().coerceIn(0, sorted.size - 1)
    val hi = (lo + 1).coerceIn(0, sorted.size - 1)
    val frac = idx - lo
    return sorted[lo] + (sorted[hi] - sorted[lo]) * frac
}

fun medianOf(values: Collection<Double>): Double {
    if (values.isEmpty()) return 0.0
    val a = values.toDoubleArray()
    a.sort()
    return percentileSorted(a, 0.5)
}

fun percentileOf(values: Collection<Double>, p: Double): Double {
    if (values.isEmpty()) return 0.0
    val a = values.toDoubleArray()
    a.sort()
    return percentileSorted(a, p)
}

data class LinFit(
    val slope: Double,      // units of y per unit of x
    val intercept: Double,
    val r2: Double,         // coefficient of determination, 0..1
    val n: Int
)

/**
 * Ordinary least squares fit of y = slope*x + intercept.
 * Arrays are read only up to [n] entries.
 */
fun linearRegression(xs: DoubleArray, ys: DoubleArray, n: Int): LinFit {
    if (n < 2) return LinFit(0.0, ys.getOrElse(0) { 0.0 }, 0.0, n)
    var sx = 0.0
    var sy = 0.0
    for (i in 0 until n) {
        sx += xs[i]
        sy += ys[i]
    }
    val mx = sx / n
    val my = sy / n
    var sxx = 0.0
    var sxy = 0.0
    var syy = 0.0
    for (i in 0 until n) {
        val dx = xs[i] - mx
        val dy = ys[i] - my
        sxx += dx * dx
        sxy += dx * dy
        syy += dy * dy
    }
    val slope = if (sxx > 1e-12) sxy / sxx else 0.0
    val r2 = if (syy > 1e-12) (sxy * sxy) / (sxx * syy) else 0.0
    return LinFit(slope, my - slope * mx, r2.coerceIn(0.0, 1.0), n)
}

/** dBm -> linear power (arbitrary unit, milliWatts scaled). */
fun dbmToLinear(dbm: Double): Double = 10.0.pow(dbm / 10.0)

/** Soft, smooth 0..1 saturation used to keep heuristics bounded. */
fun saturate(x: Double): Double = x / (1.0 + kotlin.math.abs(x))

fun FloatArray.meanOf(n: Int = size): Float {
    if (n == 0) return 0f
    var s = 0f
    for (i in 0 until n) s += this[i]
    return s / n
}
