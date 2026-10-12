package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WatchedTotalsTest {
    private fun status(json: String) = AppJson.decodeFromString(UserStatus.serializer(), json)

    @Test
    fun theTotalsAreReadFromPlugin21() {
        val s = status("""{"UserId":"u","Username":"Penelope","ApiLevel":3,"WatchedTodaySeconds":2400.5,"WatchedWeekSeconds":11000,"WatchedMonthSeconds":40000,"WatchedYearSeconds":300000,"WatchedTrackingSince":"2026-10-12"}""")
        assertTrue(s.reportsWatchedTotals)
        assertEquals(2400.5, s.watchedTodaySeconds!!, 0.001)
        assertEquals(300000.0, s.watchedYearSeconds!!, 0.001)
        assertEquals("2026-10-12", s.watchedTrackingSince)
    }

    @Test
    fun anOlderPluginHasNoTotalsAndKeepsTheOldBehaviour() {
        val s = status("""{"UserId":"u","Username":"Wyatt","ApiLevel":2,"SecondsWatchedWeekly":600}""")
        assertFalse(s.reportsWatchedTotals)
        assertNull(s.watchedWeekSeconds)
        // level 3 without the numbers is not trusted either
        assertFalse(status("""{"UserId":"u","ApiLevel":3}""").reportsWatchedTotals)
    }

    @Test
    fun totalsAreWrittenInHoursAndMinutes() {
        assertEquals("0 min", WatchText.total(0.0))
        assertEquals("0 min", WatchText.total(59.9))
        assertEquals("1 min", WatchText.total(60.0))
        assertEquals("42 min", WatchText.total(2520.0))
        assertEquals("1 h 05 min", WatchText.total(3900.0))
        assertEquals("120 h 00 min", WatchText.total(432000.0))
        assertEquals("0 min", WatchText.total(-5.0))
    }

    @Test
    fun theFootnoteSaysWhenCountingStartedOnlyWhileItMatters() {
        val today = LocalDate.of(2026, 10, 20)
        val note = WatchText.trackingNote(listOf("2026-10-12", null, "2026-10-15"), today)
        assertNotNull(note)
        assertTrue(note!!.contains("2026") || note.contains("Oct"))
        // counted since last year or earlier: every total of this year is complete, so no note
        assertNull(WatchText.trackingNote(listOf("2025-03-01"), today))
        assertNull(WatchText.trackingNote(listOf(null, "", "garbage"), today))
        assertNull(WatchText.trackingNote(emptyList(), today))
        assertNull(WatchText.trackingNote(listOf("2026-12-01"), today)) // a start day in the future is nonsense
    }
}
