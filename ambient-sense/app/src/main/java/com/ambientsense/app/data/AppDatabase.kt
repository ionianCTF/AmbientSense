package com.ambientsense.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AmbientDao {

    @Insert
    suspend fun insertSample(sample: SampleEntity): Long

    @Insert
    suspend fun insertLabel(label: LabelEntity): Long

    @Query("SELECT * FROM samples ORDER BY t DESC LIMIT :limit")
    fun recentSamples(limit: Int): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE t BETWEEN :from AND :to ORDER BY t ASC")
    suspend fun samplesBetween(from: Long, to: Long): List<SampleEntity>

    @Query("SELECT * FROM labels ORDER BY t DESC LIMIT :limit")
    fun recentLabels(limit: Int): Flow<List<LabelEntity>>

    @Query("SELECT COUNT(*) FROM labels")
    fun labelCount(): Flow<Int>

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteLabel(id: Long)

    @Query("DELETE FROM labels")
    suspend fun clearLabels()

    @Query("DELETE FROM samples")
    suspend fun clearSamples()

    @Query("SELECT COUNT(*) FROM samples")
    suspend fun sampleCount(): Int
}

@Database(
    entities = [SampleEntity::class, LabelEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AmbientDao

    companion object {
        /** v1 -> v2: remember the rules-only baseline alongside each label. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE labels ADD COLUMN peopleRules REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE labels ADD COLUMN vehicleRules REAL NOT NULL DEFAULT 0")
            }
        }
    }
}
