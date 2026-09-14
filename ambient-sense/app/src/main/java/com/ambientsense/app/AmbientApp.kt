package com.ambientsense.app

import android.app.Application
import androidx.room.Room
import com.ambientsense.app.data.AppDatabase
import com.ambientsense.app.data.SettingsStore
import com.ambientsense.app.sensing.CalibrationModels
import com.ambientsense.app.sensing.FusionEngine

/**
 * Holds the process-wide singletons: settings, the measurement database and the two
 * online calibration models. Kept deliberately tiny — no DI framework needed.
 */
class AmbientApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var models: CalibrationModels
        private set
    lateinit var fusion: FusionEngine
        private set

    override fun onCreate() {
        super.onCreate()
        // osmdroid refuses to fetch tiles without a user agent identifying the app
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@AmbientApp, android.preference.PreferenceManager.getDefaultSharedPreferences(this@AmbientApp))
        }
        settings = SettingsStore(this)
        database = Room.databaseBuilder(this, AppDatabase::class.java, "ambient.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        models = CalibrationModels(settings.rawPrefs())
        fusion = FusionEngine({ settings.current }, models)
    }
}
