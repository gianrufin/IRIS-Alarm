package com.iris.alarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlarmEntity::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class IrisDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        const val NAME = "iris.db"

        /**
         * Adds the per-alarm overrides of the global auto-silence and volume ramp.
         * Both are nullable and left null, so every existing alarm keeps following
         * the global setting exactly as it did before the upgrade.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN autoSilenceMinutes INTEGER")
                db.execSQL("ALTER TABLE alarms ADD COLUMN volumeRampSeconds INTEGER")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
