package com.bratneytg.ganga

import com.bratneytg.ganga.work.Scheduling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val times = listOf("00:00", "06:00", "12:00", "18:00")

    @Test
    fun picksNextSlotToday() {
        val now = ZonedDateTime.of(2026, 11, 26, 7, 30, 0, 0, zone)
        val next = Scheduling.nextSlot(now, times)!!
        assertEquals(12, next.hour)
        assertEquals(26, next.dayOfMonth)
    }

    @Test
    fun rollsOverToTomorrow() {
        val now = ZonedDateTime.of(2026, 11, 26, 18, 0, 0, 0, zone)
        val next = Scheduling.nextSlot(now, times)!!
        assertEquals(0, next.hour)
        assertEquals(27, next.dayOfMonth)
    }

    @Test
    fun ignoresInvalidTimes() {
        val now = ZonedDateTime.of(2026, 11, 26, 7, 30, 0, 0, zone)
        assertNull(Scheduling.nextSlot(now, listOf("xx", "")))
    }
}
