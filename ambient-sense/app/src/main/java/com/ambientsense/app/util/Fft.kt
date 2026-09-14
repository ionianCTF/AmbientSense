package com.ambientsense.app.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Minimal in-place iterative radix-2 Cooley–Tukey FFT.
 * Used by the sound level meter to A-weight the spectrum.
 */
object Fft {

    fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    /** In-place forward transform. [re]/[im] must have equal, power-of-two length. */
    fun forward(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(isPowerOfTwo(n)) { "FFT length must be a power of two (was $n)" }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            val half = len shr 1
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr
                    im[b] = im[a] - vi
                    re[a] = re[a] + vr
                    im[a] = im[a] + vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Hann window coefficients of length [n]. */
    fun hann(n: Int): DoubleArray {
        val w = DoubleArray(n)
        for (i in 0 until n) {
            w[i] = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
        }
        return w
    }

    /**
     * A-weighting gain (linear, not dB) at frequency [f] Hz.
     * Standard IEC 61672-1 A-weighting curve.
     */
    fun aWeightingLinear(f: Double): Double {
        if (f <= 0.0) return 0.0
        val f2 = f * f
        val num = 12194.0 * 12194.0 * f2 * f2
        val den =
            (f2 + 20.6 * 20.6) *
                kotlin.math.sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) *
                (f2 + 12194.0 * 12194.0)
        val ra = num / den
        val db = 20.0 * kotlin.math.log10(ra) + 2.0
        return 10.0.pow(db / 10.0)
    }
}
