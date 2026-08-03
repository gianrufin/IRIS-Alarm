package com.iris.alarm.data.local

import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.VisionChallenge
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmEntityTest {

    @Test
    fun `round trip preserves every field`() {
        val alarm = Alarm(
            id = 42,
            hour = 6,
            minute = 45,
            label = "Gym",
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            challenge = VisionChallenge.ANCHOR,
            anchorSignature = "123,0.1",
            anchorThumbnailPath = "/data/anchors/a.png",
            soundUri = "content://media/alarm/7",
            vibrate = false,
            enabled = true,
            autoSilenceMinutes = 30,
            volumeRampSeconds = 0,
        )

        assertEquals(alarm, alarm.toEntity().toDomain())
    }

    @Test
    fun `repeat mask uses one bit per day`() {
        val monday = Alarm(hour = 0, minute = 0, repeatDays = setOf(DayOfWeek.MONDAY))
        val sunday = Alarm(hour = 0, minute = 0, repeatDays = setOf(DayOfWeek.SUNDAY))

        assertEquals(0b0000001, monday.toEntity().repeatMask)
        assertEquals(0b1000000, sunday.toEntity().repeatMask)
    }

    @Test
    fun `empty repeat set round trips as a one-shot`() {
        val alarm = Alarm(hour = 8, minute = 0)

        val restored = alarm.toEntity().toDomain()

        assertEquals(emptySet<DayOfWeek>(), restored.repeatDays)
        assertEquals(0, alarm.toEntity().repeatMask)
    }
}
