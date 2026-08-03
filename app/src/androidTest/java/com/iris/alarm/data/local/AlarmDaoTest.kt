package com.iris.alarm.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iris.alarm.data.repository.AlarmRepositoryImpl
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.VisionChallenge
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room round-trip against a real SQLite instance, including the enum converters. */
@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {

    private lateinit var database: IrisDatabase
    private lateinit var repository: AlarmRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IrisDatabase::class.java,
        ).build()
        repository = AlarmRepositoryImpl(database.alarmDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun insertedAlarmsComeBackIntact() = runTest {
        val id = repository.upsert(
            Alarm(
                hour = 6,
                minute = 45,
                label = "Gym",
                repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
                challenge = VisionChallenge.ANCHOR,
                anchorSignature = "42,0.5",
                vibrate = false,
            ),
        )

        val stored = repository.getAlarm(id)!!

        assertEquals(6, stored.hour)
        assertEquals("Gym", stored.label)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), stored.repeatDays)
        assertEquals(VisionChallenge.ANCHOR, stored.challenge)
        assertEquals("42,0.5", stored.anchorSignature)
        assertFalse(stored.vibrate)
    }

    @Test
    fun alarmsAreOrderedByTimeOfDay() = runTest {
        repository.upsert(Alarm(hour = 9, minute = 0))
        repository.upsert(Alarm(hour = 6, minute = 30))
        repository.upsert(Alarm(hour = 6, minute = 15))

        val times = repository.observeAlarms().first().map { it.hour to it.minute }

        assertEquals(listOf(6 to 15, 6 to 30, 9 to 0), times)
    }

    @Test
    fun togglingEnabledIsPersistedAndFiltersTheEnabledQuery() = runTest {
        val id = repository.upsert(Alarm(hour = 7, minute = 0))

        repository.setEnabled(id, enabled = false)

        assertFalse(repository.getAlarm(id)!!.enabled)
        assertTrue(repository.getEnabledAlarms().isEmpty())
    }

    @Test
    fun deletingRemovesTheAlarm() = runTest {
        val id = repository.upsert(Alarm(hour = 7, minute = 0))
        val stored = repository.getAlarm(id)!!

        repository.delete(stored)

        assertEquals(null, repository.getAlarm(id))
    }
}
