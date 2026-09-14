package com.ambientsense.app.sensing

import android.content.SharedPreferences
import com.ambientsense.app.data.SenseMode
import com.ambientsense.app.data.SenseSettings
import com.ambientsense.app.model.BleMetrics
import com.ambientsense.app.model.DeviceTrack
import com.ambientsense.app.model.FeatureContribution
import com.ambientsense.app.model.FusionResult
import com.ambientsense.app.model.MotionClass
import com.ambientsense.app.model.SenseRadius
import com.ambientsense.app.model.NetworkMetrics
import com.ambientsense.app.model.NoiseMetrics
import com.ambientsense.app.model.WifiMetrics
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Combines the RF, device-tracking and acoustic channels into two numbers the user
 * cares about: **how many people** and **how many vehicles** are around right now.
 *
 * Two estimators run in parallel:
 *
 *  - **Rules** — a physics-flavoured heuristic that needs no training. It blends a
 *    *device-based* term (clustered BLE entities within the proximity radius, scaled by
 *    the fraction of people who carry a discoverable device) with a *device-free* term
 *    (Wi-Fi power fluctuation and blocking-event rate, which responds to bodies and
 *    metal masses that carry nothing at all).
 *  - **Model** — an online RLS regressor that learns the *correction* to those rules
 *    from the ground truth the user labels. It starts at zero influence and takes over
 *    smoothly as labels accumulate.
 */
class FusionEngine(
    private val settings: () -> SenseSettings,
    val models: CalibrationModels
) {

    fun fuse(
        wifi: WifiMetrics,
        ble: BleMetrics,
        noise: NoiseMetrics,
        net: NetworkMetrics,
        nowMs: Long
    ): FusionResult {
        val s = settings()

        val entitiesNear = peopleLikeCount(ble.tracks, s.proximityRadiusM, s)
        val vehicleEntities = countEntities(ble.tracks, MotionClass.VEHICLE, s.vehicleRadiusM)
        val mobileEntities = (ble.entityCount - ble.backgroundCount).coerceAtLeast(0)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) +
            Calendar.getInstance().get(Calendar.MINUTE) / 60f
        // The device-free channel looks at a volume fixed by the access points, not by
        // the radius the user picked, so its prior is rescaled to the region of
        // interest. Measured: alpha(R) = (R/25)^0.50.
        val rfScale = SenseRadius.rfScale(s.proximityRadiusM)
        // The learned correction is an absolute headcount; put it in region units so
        // labels taken at one radius stay useful at another.
        val residScale = SenseRadius.residualScale(s.proximityRadiusM).toDouble()

        // ---------------------------------------------------------------- rules
        val deviceTerm = s.deviceToPersonFactor * entitiesNear
        val rfTerm = if (s.mode.usesWifi) {
            rfScale * (s.rfPersonCoeff * wifi.cvMedian * sqrt(wifi.linksTracked.toFloat().coerceAtLeast(1f)) +
                s.dipPersonCoeff * wifi.dipRatePerMin)
        } else 0f
        val peopleHeuristic = when (s.mode) {
            SenseMode.BLE -> deviceTerm
            SenseMode.WIFI -> rfTerm
            SenseMode.BOTH -> RULES_DEVICE_WEIGHT * deviceTerm + RULES_RF_WEIGHT * rfTerm
        }

        val vehicleHeuristic = vehicleEntities +
            if (s.mode.usesWifi) s.vehicleDipCoeff * wifi.dipRatePerMin else 0f

        // -------------------------------------------------------------- features
        val peopleFeatures = doubleArrayOf(
            peopleHeuristic.toDouble(),
            mobileEntities.toDouble(),
            entitiesNear.toDouble(),
            ble.pedestrianCount.toDouble(),
            ble.staticCount.toDouble(),
            ble.vehicleCount.toDouble(),
            wifi.cvMedian.toDouble(),
            wifi.cvP90.toDouble(),
            wifi.rssiStdMedian.toDouble(),
            wifi.dipRatePerMin.toDouble(),
            wifi.churnPerMin.toDouble(),
            wifi.apCount.toDouble(),
            (noise.leqDba ?: 0f).toDouble(),
            hour.toDouble()
        )

        val vehicleFeatures = doubleArrayOf(
            vehicleHeuristic.toDouble(),
            vehicleEntities.toDouble(),
            mobileEntities.toDouble(),
            wifi.dipRatePerMin.toDouble(),
            wifi.cvP90.toDouble(),
            wifi.apCount.toDouble(),
            (noise.leqDba ?: 0f).toDouble(),
            hour.toDouble()
        )

        // ---------------------------------------------------------------- model
        val peopleTrust = models.people.trustWeight()
        val vehicleTrust = models.vehicles.trustWeight()

        val peopleCorrection = models.people.predict(peopleFeatures) * peopleTrust * residScale
        val vehicleCorrection = models.vehicles.predict(vehicleFeatures) * vehicleTrust * residScale

        val people = (peopleHeuristic + peopleCorrection).toFloat().coerceIn(0f, MAX_PEOPLE)
        val vehicles = (vehicleHeuristic + vehicleCorrection).toFloat().coerceIn(0f, MAX_VEHICLES)

        // ------------------------------------------------------------ confidence
        val sensorQuality = when (s.mode) {
            SenseMode.BOTH ->
                0.5f * (wifi.linksTracked / 8f).coerceIn(0f, 1f) +
                    0.5f * (ble.entityCount / 4f).coerceIn(0f, 1f)
            SenseMode.WIFI -> (wifi.linksTracked / 8f).coerceIn(0f, 1f)
            SenseMode.BLE -> (ble.entityCount / 4f).coerceIn(0f, 1f)
        }
        val disagreement = if (s.mode == SenseMode.BOTH && peopleHeuristic > 0.5f) {
            (abs(deviceTerm - rfTerm) / peopleHeuristic).coerceIn(0f, 1f)
        } else 0f
        val confidence = (
            BASE_CONFIDENCE +
                MODEL_CONFIDENCE_GAIN * ((peopleTrust + vehicleTrust) / 2f) +
                SENSOR_CONFIDENCE_GAIN * sensorQuality -
                DISAGREEMENT_PENALTY * disagreement
            ).coerceIn(0.05f, 0.97f)

        val note = when {
            models.people.labelCount == 0 -> "Physics baseline — label the crowd to calibrate"
            peopleTrust < 1f -> "Calibrating… ${models.people.labelCount} labels"
            else -> "Calibrated on ${models.people.labelCount} labels"
        }

        val contributions = if (models.people.labelCount > 0) {
            models.people.explain(peopleFeatures).map { (name, contribution) ->
                FeatureContribution(name, featureValue(name, peopleFeatures), contribution)
            }.sortedByDescending { abs(it.weight) }.take(6)
        } else emptyList()

        return FusionResult(
            people = people,
            vehicles = vehicles,
            peopleRaw = peopleHeuristic,
            vehiclesRaw = vehicleHeuristic,
            confidence = confidence,
            modelWeight = (peopleTrust + vehicleTrust) / 2f,
            features = contributions,
            note = note,
            atMs = nowMs
        )
    }

    /**
     * Called when the user submits ground truth. The model learns the residual
     * (truth − rules); storing the residual instead of the absolute count means a
     * handful of labels already helps, and a bad label can't throw the estimator far
     * off the physical ballpark.
     */
    fun submitLabel(
        truthPeople: Int,
        truthVehicles: Int,
        wifi: WifiMetrics,
        ble: BleMetrics,
        noise: NoiseMetrics,
        net: NetworkMetrics,
        nowMs: Long
    ): Pair<Float, Float> {
        val s = settings()
        val entitiesNear = peopleLikeCount(ble.tracks, s.proximityRadiusM, s)
        val vehicleEntities = countEntities(ble.tracks, MotionClass.VEHICLE, s.vehicleRadiusM)
        val mobileEntities = (ble.entityCount - ble.backgroundCount).coerceAtLeast(0)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) +
            Calendar.getInstance().get(Calendar.MINUTE) / 60f
        val rfScale = SenseRadius.rfScale(s.proximityRadiusM)
        val residScale = SenseRadius.residualScale(s.proximityRadiusM).toDouble()

        val deviceTerm = s.deviceToPersonFactor * entitiesNear
        val rfTerm = rfScale * (s.rfPersonCoeff * wifi.cvMedian *
            sqrt(wifi.linksTracked.toFloat().coerceAtLeast(1f)) +
            s.dipPersonCoeff * wifi.dipRatePerMin)
        val peopleHeuristic = when (s.mode) {
            SenseMode.BLE -> deviceTerm
            SenseMode.WIFI -> rfTerm
            SenseMode.BOTH -> RULES_DEVICE_WEIGHT * deviceTerm + RULES_RF_WEIGHT * rfTerm
        }
        val vehicleHeuristic = vehicleEntities +
            if (s.mode.usesWifi) s.vehicleDipCoeff * wifi.dipRatePerMin else 0f

        val peopleFeatures = doubleArrayOf(
            peopleHeuristic.toDouble(), mobileEntities.toDouble(), entitiesNear.toDouble(),
            ble.pedestrianCount.toDouble(), ble.staticCount.toDouble(), ble.vehicleCount.toDouble(),
            wifi.cvMedian.toDouble(), wifi.cvP90.toDouble(), wifi.rssiStdMedian.toDouble(),
            wifi.dipRatePerMin.toDouble(), wifi.churnPerMin.toDouble(), wifi.apCount.toDouble(),
            (noise.leqDba ?: 0f).toDouble(), hour.toDouble()
        )
        val vehicleFeatures = doubleArrayOf(
            vehicleHeuristic.toDouble(), vehicleEntities.toDouble(), mobileEntities.toDouble(),
            wifi.dipRatePerMin.toDouble(), wifi.cvP90.toDouble(), wifi.apCount.toDouble(),
            (noise.leqDba ?: 0f).toDouble(), hour.toDouble()
        )

        models.people.update(peopleFeatures, (truthPeople - peopleHeuristic.toDouble()) / residScale)
        models.vehicles.update(vehicleFeatures, (truthVehicles - vehicleHeuristic.toDouble()) / residScale)
        return peopleHeuristic to vehicleHeuristic
    }

    fun featureCsv(
        wifi: WifiMetrics, ble: BleMetrics, noise: NoiseMetrics, nowMs: Long
    ): String {
        val s = settings()
        val entitiesNear = peopleLikeCount(ble.tracks, s.proximityRadiusM, s)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) +
            Calendar.getInstance().get(Calendar.MINUTE) / 60f
        return listOf(
            nowMs, wifi.apCount, wifi.linksTracked, wifi.cvMedian, wifi.cvP90,
            wifi.rssiStdMedian, wifi.dipRatePerMin, wifi.churnPerMin, wifi.activityIndex,
            ble.entityCount, entitiesNear, ble.staticCount, ble.pedestrianCount,
            ble.vehicleCount, ble.vehiclesPassed, (noise.leqDba ?: 0f), hour
        ).joinToString(",")
    }

    private fun featureValue(name: String, features: DoubleArray): Float {
        val idx = PEOPLE_FEATURES.indexOf(name)
        return if (idx >= 0) features[idx].toFloat() else 0f
    }

    /**
     * Person-like entities inside [radiusM].
     *
     * Two corrections that mattered a lot in validation:
     *  - a car is not a person, so vehicle-classified entities are excluded;
     *  - a motionless cluster is far more likely to be a parked car or a laptop than
     *    a standing person, so standing entities carry [SenseSettings.staticEntityWeight].
     *  - long-lived, perfectly still devices (infrastructure) do not count at all.
     */
    private fun peopleLikeCount(tracks: List<DeviceTrack>, radiusM: Float, s: SenseSettings): Float {
        return groupTracks(tracks).sumOf { (lead, members) ->
            when {
                lead.infrastructure -> 0.0
                members.minOf { it.distanceM } > radiusM -> 0.0
                lead.motion == MotionClass.VEHICLE -> 0.0
                lead.motion == MotionClass.PEDESTRIAN -> 1.0
                else -> s.staticEntityWeight.toDouble()
            }
        }.toFloat()
    }

    private fun countEntities(tracks: List<DeviceTrack>, cls: MotionClass, radiusM: Float): Int {
        return groupTracks(tracks).count { (lead, _) ->
            !lead.infrastructure && lead.motion == cls && lead.distanceM <= radiusM
        }
    }

    /**
     * Groups tracks into physical entities; for each group the "lead" device is the
     * one with the largest |radial speed|, since that is the member that best
     * characterises the motion of the whole cluster.
     */
    private fun groupTracks(tracks: List<DeviceTrack>): List<Pair<DeviceTrack, List<DeviceTrack>>> {
        val buckets = LinkedHashMap<String, MutableList<DeviceTrack>>()
        tracks.forEach { t ->
            val key = if (t.groupId >= 0) "g${t.groupId}" else "s${t.key}"
            buckets.getOrPut(key) { ArrayList() }.add(t)
        }
        return buckets.values.map { members ->
            (members.maxByOrNull { abs(it.radialSpeedMps) } ?: members.first()) to members
        }
    }

    companion object {
        val PEOPLE_FEATURES = listOf(
            "rules estimate", "ble entities", "entities nearby", "walkers", "standing",
            "vehicles", "wifi cv median", "wifi cv p90", "wifi std dB",
            "dip rate /min", "ap churn /min", "access points", "noise dB(A)", "hour of day"
        )
        val VEHICLE_FEATURES = listOf(
            "rules estimate", "vehicle entities", "ble entities", "dip rate /min",
            "wifi cv p90", "access points", "noise dB(A)", "hour of day"
        )

        // Measured in validation: BLE carries most of the signal, the Wi-Fi channel
        // is worth a quarter of the blend once its prior is radius-scaled.
        private const val RULES_DEVICE_WEIGHT = 0.75f
        private const val RULES_RF_WEIGHT = 0.25f
        private const val BASE_CONFIDENCE = 0.30f
        private const val MODEL_CONFIDENCE_GAIN = 0.32f
        private const val SENSOR_CONFIDENCE_GAIN = 0.30f
        private const val DISAGREEMENT_PENALTY = 0.18f
        private const val MAX_PEOPLE = 500f
        private const val MAX_VEHICLES = 200f
    }
}

/** Both online models, sharing one preferences file. */
class CalibrationModels(prefs: SharedPreferences) {
    val people = CalibrationModel(prefs, "model_people_v2", FusionEngine.PEOPLE_FEATURES)
    val vehicles = CalibrationModel(prefs, "model_vehicles_v2", FusionEngine.VEHICLE_FEATURES)

    fun reset() {
        people.reset()
        vehicles.reset()
    }

    /**
     * Drop confidence in the current weights without discarding them — used when the
     * user changes the sensing radius, because a model fitted for one region does not
     * transfer to another.
     */
    fun decayTrust() {
        people.decayTrust()
        vehicles.decayTrust()
    }
}
