package com.ambientsense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One fused measurement, geotagged, written periodically during a session. */
@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val t: Long,
    val lat: Double?,
    val lon: Double?,
    val accuracyM: Float?,

    val people: Float,
    val vehicles: Float,
    val peopleRules: Float,
    val vehicleRules: Float,
    val confidence: Float,

    val wifiApCount: Int,
    val wifiCvMedian: Float,
    val wifiDipRate: Float,
    val wifiChurn: Float,

    val bleEntities: Int,
    val bleNearby: Int,
    val blePedestrian: Int,
    val bleVehicle: Int,
    val bleStatic: Int,

    val noiseDba: Float?,
    val downMbps: Float?,
    val upMbps: Float?,
    val pingMs: Float?,
    val jitterMs: Float?,
    val lossPct: Float?,
    val netType: String?,
    val rsrpDbm: Int?
)

/** Ground truth the user typed in, with the feature vector that produced it. */
@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val t: Long,
    val lat: Double?,
    val lon: Double?,
    val peopleTruth: Int,
    val vehicleTruth: Int,
    val noiseTruthDba: Float?,
    /** What the app was showing for people at that moment (before this label was learned). */
    val peopleEstimate: Float,
    val vehicleEstimate: Float,
    /** The rules-only baseline at the same moment, so error can be attributed. */
    val peopleRules: Float = 0f,
    val vehicleRules: Float = 0f,
    val note: String?,
    /** CSV of the feature vector, column order documented in CsvExporter. */
    val features: String
)
