package com.iris.alarm.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTest {

    // A Wednesday, so "later today" and "wrap to next week" are both reachable.
    private val wednesdayMorning = LocalDateTime.of(2026, 1, 7, 7, 0)

    private fun Long.toLocal(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

    @Test
    fun `one-shot alarm later today fires today`() {
        val alarm = Alarm(hour = 9, minute = 30)

        val next = alarm.nextTriggerAtMillis(wednesdayMorning)!!.toLocal()

        assertEquals(LocalDateTime.of(2026, 1, 7, 9, 30), next)
    }

    @Test
    fun `one-shot alarm already past today rolls to tomorrow`() {
        val alarm = Alarm(hour = 6, minute = 0)

        val next = alarm.nextTriggerAtMillis(wednesdayMorning)!!.toLocal()

        assertEquals(LocalDateTime.of(2026, 1, 8, 6, 0), next)
    }

    @Test
    fun `repeating alarm picks the next matching weekday`() {
        val alarm = Alarm(hour = 6, minute = 0, repeatDays = setOf(DayOfWeek.FRIDAY))

        val next = alarm.nextTriggerAtMillis(wednesdayMorning)!!.toLocal()

        assertEquals(LocalDateTime.of(2026, 1, 9, 6, 0), next)
    }

    @Test
    fun `repeating alarm on today fires today when the time has not passed`() {
        val alarm = Alarm(hour = 22, minute = 0, repeatDays = setOf(DayOfWeek.WEDNESDAY))

        val next = alarm.nextTriggerAtMillis(wednesdayMorning)!!.toLocal()

        assertEquals(LocalDateTime.of(2026, 1, 7, 22, 0), next)
    }

    @Test
    fun `repeating alarm on today wraps a week when the time has passed`() {
        val alarm = Alarm(hour = 6, minute = 0, repeatDays = setOf(DayOfWeek.WEDNESDAY))

        val next = alarm.nextTriggerAtMillis(wednesdayMorning)!!.toLocal()

        assertEquals(LocalDateTime.of(2026, 1, 14, 6, 0), next)
    }

    @Test
    fun `disabled alarm never schedules`() {
        val alarm = Alarm(hour = 9, minute = 0, enabled = false)

        assertNull(alarm.nextTriggerAtMillis(wednesdayMorning))
    }

    @Test
    fun `every day is always within the next 24 hours`() {
        val alarm = Alarm(hour = 6, minute = 0, repeatDays = DayOfWeek.values().toSet())

        val next = alarm.nextTriggerAtMillis(wednesdayMorning)!!.toLocal()

        assertTrue(next.isAfter(wednesdayMorning))
        assertTrue(next.isBefore(wednesdayMorning.plusDays(1)))
    }
}
