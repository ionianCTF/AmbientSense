package com.ambientsense.app.model

import kotlin.math.pow

/**
 * The sensing radius — how far out the app reports on — and the accuracy you can
 * expect at each setting.
 *
 * Every number in [OPTIONS] was *measured*, not guessed, by
 * `validation/radius.py` (E8): 8 radii x 10 simulated street scenes x 300 s each,
 * with the same estimator this app runs. Truth is "people whose distance to the
 * phone is <= R right now", i.e. exactly the question the radius asks.
 *
 *  * `maeFresh`      mean absolute people-count error with no labels at this radius
 *  * `maeCalibrated` mean absolute error after ~25 labels at this radius
 *  * `withinOne`     P(|error| <= 1 person) after ~25 labels
 *  * `coverage`      fraction of the people inside R that BLE actually samples
 *                    directly; the rest of the count is inferred by the model
 *  * `vehicleRecall` fraction of vehicles that pass within R and are detected
 *
 * Two closed-form laws came out of the same experiment:
 *
 *  * `rfScale`        the device-free (Wi-Fi) term has a field of view set by the
 *                     access-point geometry, not by R, so its prior is rescaled by
 *                     (R/25)^0.50 — a square-root law, not an area law. Best fit
 *                     over 8 radii, rms log residual 0.14.
 *  * `residualScale`  the learned correction is an absolute headcount, so it has to
 *                     be divided by (R/25)^0.48 when training and multiplied by the
 *                     same factor when predicting. That makes labels portable
 *                     between radii instead of being stuck in the units of the
 *                     radius they were taken at.
 *
 * Caveat that must travel with these numbers: they come from a physics simulator,
 * not from a field trial. They are the best estimate available before real data
 * exists, and the Accuracy tab replaces them with the real thing as you label.
 */
object SenseRadius {

    /** Radius the laws are expressed relative to (metres). */
    const val REFERENCE_M = 25f

    /** Fitted exponent of the RF prior, alpha(R) = (R/25)^RF_EXPONENT. */
    const val RF_EXPONENT = 0.505f

    /** Fitted exponent of the learned-correction scale, s(R) = (R/25)^RESIDUAL_EXPONENT. */
    const val RESIDUAL_EXPONENT = 0.481f

    data class Option(
        val meters: Float,
        val name: String,
        val blurb: String,
        /** Mean |error| in people, no labels yet. */
        val maeFresh: Float,
        /** Mean |error| in people, after ~25 labels at this radius. */
        val maeCalibrated: Float,
        /** P(|error| <= 1) after ~25 labels. */
        val withinOne: Float,
        /** P(|error| <= 1) with no labels. */
        val withinOneFresh: Float,
        /** Share of the crowd inside R that the radios sample directly. */
        val coverage: Float,
        /** Share of vehicles passing within R that get detected. */
        val vehicleRecall: Float,
        /** True when this is the setting the app ships with. */
        val recommended: Boolean = false
    )

    val OPTIONS: List<Option> = listOf(
        Option(
            meters = 5f, name = "Arm's length",
            blurb = "A desk, a bench, a doorway. Almost everything the radio hears here is " +
                "static infrastructure rather than people, so the count is mostly clutter " +
                "until you calibrate.",
            maeFresh = 0.50f, maeCalibrated = 0.16f, withinOne = 1.00f, withinOneFresh = 0.99f,
            coverage = 0.07f, vehicleRecall = 0.00f
        ),
        Option(
            meters = 10f, name = "Room",
            blurb = "One room or a small garden. Best absolute accuracy of any setting, " +
                "but it only reports on a bubble around the phone.",
            maeFresh = 0.67f, maeCalibrated = 0.29f, withinOne = 0.98f, withinOneFresh = 0.81f,
            coverage = 0.10f, vehicleRecall = 0.29f
        ),
        Option(
            meters = 15f, name = "Pavement",
            blurb = "The width of a pavement and the kerb. The best radius for catching " +
                "vehicles: a car is still close enough that its range curve is clean.",
            maeFresh = 0.99f, maeCalibrated = 0.51f, withinOne = 0.90f, withinOneFresh = 0.56f,
            coverage = 0.13f, vehicleRecall = 0.40f
        ),
        Option(
            meters = 20f, name = "Frontage",
            blurb = "A shop frontage or a driveway. Still inside reliable BLE range for " +
                "most phones.",
            maeFresh = 1.18f, maeCalibrated = 0.70f, withinOne = 0.76f, withinOneFresh = 0.48f,
            coverage = 0.17f, vehicleRecall = 0.32f
        ),
        Option(
            meters = 30f, name = "Street",
            blurb = "Both sides of a street. The widest radius at which BLE still samples " +
                "as much of the crowd as it ever will — beyond this you are asking the " +
                "model to extrapolate, not to measure.",
            maeFresh = 1.31f, maeCalibrated = 0.82f, withinOne = 0.65f, withinOneFresh = 0.47f,
            coverage = 0.18f, vehicleRecall = 0.30f, recommended = true
        ),
        Option(
            meters = 45f, name = "Crossroads",
            blurb = "A junction. Phones at 30-45 m are usually below the receiver " +
                "sensitivity floor, so most of this count is inferred.",
            maeFresh = 1.42f, maeCalibrated = 0.92f, withinOne = 0.60f, withinOneFresh = 0.45f,
            coverage = 0.17f, vehicleRecall = 0.30f
        ),
        Option(
            meters = 60f, name = "Square",
            blurb = "A small square. Error stops improving much past 45 m: you are " +
                "widening the question, not the evidence.",
            maeFresh = 1.58f, maeCalibrated = 0.91f, withinOne = 0.65f, withinOneFresh = 0.42f,
            coverage = 0.15f, vehicleRecall = 0.30f
        ),
        Option(
            meters = 90f, name = "Horizon",
            blurb = "The absolute limit of what a phone can hear at 2.4 GHz. Offered for " +
                "completeness: error more than doubles and coverage halves versus 30 m.",
            maeFresh = 2.14f, maeCalibrated = 1.16f, withinOne = 0.52f, withinOneFresh = 0.28f,
            coverage = 0.12f, vehicleRecall = 0.30f
        )
    )

    /** Multiplier for the device-free Wi-Fi prior at radius [meters]. */
    fun rfScale(meters: Float): Float =
        (meters / REFERENCE_M).pow(RF_EXPONENT).coerceIn(0.15f, 4f)

    /**
     * Unit conversion for the learned correction at radius [meters]: divide the
     * training residual by it, multiply the prediction by it.
     */
    fun residualScale(meters: Float): Float =
        (meters / REFERENCE_M).pow(RESIDUAL_EXPONENT).coerceIn(0.15f, 4f)

    /** The preset matching [meters], or null for a custom value. */
    fun preset(meters: Float): Option? = OPTIONS.firstOrNull { it.meters == meters }

    /** Nearest preset to a custom radius, used for the accuracy hint. */
    fun nearest(meters: Float): Option =
        OPTIONS.minByOrNull { kotlin.math.abs(it.meters - meters) } ?: OPTIONS.first()

    /**
     * Accuracy expected at [meters] with [labels] ground-truth counts at this
     * radius. Interpolated between "no labels" and "25 labels" on a 1/sqrt curve,
     * which is how RLS error typically decays early on — a shape, not a promise.
     */
    fun expectedMae(meters: Float, labels: Int): Float {
        val o = nearest(meters)
        if (labels <= 0) return o.maeFresh
        val t = (labels / 25f).coerceIn(0f, 1f)
        val shape = kotlin.math.sqrt(t.toDouble()).toFloat()
        return o.maeFresh + (o.maeCalibrated - o.maeFresh) * shape
    }
}
