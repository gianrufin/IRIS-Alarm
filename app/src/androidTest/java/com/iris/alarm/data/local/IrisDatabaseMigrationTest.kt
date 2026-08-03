package com.iris.alarm.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A destructive migration would delete somebody's alarms, and they would not
 * wake up. This asserts the v1 → v2 upgrade preserves existing rows and leaves
 * the new override columns null, so every migrated alarm keeps following the
 * global settings exactly as it did before.
 */
@RunWith(AndroidJUnit4::class)
class IrisDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        IrisDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsAlarmsAndDefaultsTheNewColumnsToNull() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms
                (id, hour, minute, label, repeatMask, challenge, huntTarget, soundUri, vibrate, enabled)
                VALUES (7, 6, 30, 'Gym', 3, 'SMILE', 'CUP', NULL, 1, 1)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            /* validateDroppedTables = */ true,
            IrisDatabase.MIGRATION_1_2,
        )

        db.query("SELECT * FROM alarms WHERE id = 7").use { cursor ->
            assertTrue("The migrated alarm is gone", cursor.moveToFirst())
            assertEquals(6, cursor.getInt(cursor.getColumnIndexOrThrow("hour")))
            assertEquals(30, cursor.getInt(cursor.getColumnIndexOrThrow("minute")))
            assertEquals("Gym", cursor.getString(cursor.getColumnIndexOrThrow("label")))
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("repeatMask")))
            assertEquals("SMILE", cursor.getString(cursor.getColumnIndexOrThrow("challenge")))

            assertTrue(
                "Migrated alarms must follow the global auto-silence setting",
                cursor.isNull(cursor.getColumnIndexOrThrow("autoSilenceMinutes")),
            )
            assertTrue(
                "Migrated alarms must follow the global volume ramp setting",
                cursor.isNull(cursor.getColumnIndexOrThrow("volumeRampSeconds")),
            )
        }
    }

    @Test
    fun migrate2To3KeepsAlarmsAndTurnsObjectHuntsIntoAnchors() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms
                (id, hour, minute, label, repeatMask, challenge, huntTarget, soundUri,
                 vibrate, enabled, autoSilenceMinutes, volumeRampSeconds)
                VALUES (11, 7, 15, 'Work', 31, 'OBJECT_HUNT', 'CUP', NULL, 1, 1, 30, 0)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO alarms
                (id, hour, minute, label, repeatMask, challenge, huntTarget, soundUri,
                 vibrate, enabled, autoSilenceMinutes, volumeRampSeconds)
                VALUES (12, 9, 0, 'Weekend', 0, 'LUMEN', 'CUP', NULL, 0, 0, NULL, NULL)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            /* validateDroppedTables = */ true,
            IrisDatabase.MIGRATION_2_3,
        )

        db.query("SELECT * FROM alarms WHERE id = 11").use { cursor ->
            assertTrue("The object-hunt alarm was lost", cursor.moveToFirst())
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("hour")))
            assertEquals("Work", cursor.getString(cursor.getColumnIndexOrThrow("label")))
            assertEquals(31, cursor.getInt(cursor.getColumnIndexOrThrow("repeatMask")))
            assertEquals(
                "Object hunts have no equivalent, so they become anchors",
                "ANCHOR",
                cursor.getString(cursor.getColumnIndexOrThrow("challenge")),
            )
            assertTrue(
                "A migrated alarm has no captured spot yet",
                cursor.isNull(cursor.getColumnIndexOrThrow("anchorSignature")),
            )
            // The per-alarm overrides added in v2 must survive the table rebuild.
            assertEquals(30, cursor.getInt(cursor.getColumnIndexOrThrow("autoSilenceMinutes")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("volumeRampSeconds")))
        }

        db.query("SELECT * FROM alarms WHERE id = 12").use { cursor ->
            assertTrue("The second alarm was lost", cursor.moveToFirst())
            assertEquals(
                "Other challenges are untouched",
                "LUMEN",
                cursor.getString(cursor.getColumnIndexOrThrow("challenge")),
            )
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("enabled")))
        }
    }

    @Test
    fun migratingAllTheWayFrom1PreservesTheAlarm() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO alarms
                (id, hour, minute, label, repeatMask, challenge, huntTarget, soundUri, vibrate, enabled)
                VALUES (3, 5, 45, 'Flight', 0, 'SMILE', 'SHOE', NULL, 1, 1)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            /* validateDroppedTables = */ true,
            *IrisDatabase.MIGRATIONS,
        )

        db.query("SELECT * FROM alarms WHERE id = 3").use { cursor ->
            assertTrue("A v1 alarm did not survive two upgrades", cursor.moveToFirst())
            assertEquals(5, cursor.getInt(cursor.getColumnIndexOrThrow("hour")))
            assertEquals("Flight", cursor.getString(cursor.getColumnIndexOrThrow("label")))
            assertEquals("SMILE", cursor.getString(cursor.getColumnIndexOrThrow("challenge")))
        }
    }

    private companion object {
        const val TEST_DB = "iris_migration_test.db"
    }
}
