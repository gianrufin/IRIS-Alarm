package com.iris.alarm.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlarmEntity::class], version = 3, exportSchema = true)
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

        /**
         * Replaces the object-hunt target with the captured place anchor. SQLite
         * cannot drop a column in this Room version, so the table is rebuilt and
         * the rows copied across — every alarm keeps its time, days and label,
         * and an alarm that was an object hunt becomes an anchor with no spot
         * captured yet.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE alarms_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        repeatMask INTEGER NOT NULL,
                        challenge TEXT NOT NULL,
                        anchorSignature TEXT,
                        anchorThumbnailPath TEXT,
                        soundUri TEXT,
                        vibrate INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        autoSilenceMinutes INTEGER,
                        volumeRampSeconds INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO alarms_new (
                        id, hour, minute, label, repeatMask, challenge,
                        anchorSignature, anchorThumbnailPath, soundUri, vibrate,
                        enabled, autoSilenceMinutes, volumeRampSeconds
                    )
                    SELECT id, hour, minute, label, repeatMask,
                        CASE challenge WHEN 'OBJECT_HUNT' THEN 'ANCHOR' ELSE challenge END,
                        NULL, NULL, soundUri, vibrate, enabled,
                        autoSilenceMinutes, volumeRampSeconds
                    FROM alarms
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE alarms")
                db.execSQL("ALTER TABLE alarms_new RENAME TO alarms")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
