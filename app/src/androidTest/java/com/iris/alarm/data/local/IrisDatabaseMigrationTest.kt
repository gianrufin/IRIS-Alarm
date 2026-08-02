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

    private companion object {
        const val TEST_DB = "iris_migration_test.db"
    }
}
