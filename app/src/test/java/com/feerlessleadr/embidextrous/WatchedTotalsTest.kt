package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

}

class TakeAwayTest {
    @org.junit.Test
    fun takesThirtyMinutesOrEverythingThatIsLeftNeverBelowZero() {
        val new = com.feerlessleadr.embidextrous.data.TakeAway.LEVEL_FOR_LIMIT
        assertEquals(30, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 3600.0, new, 0.0))
        assertEquals(30, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 1800.0, new, 0.0))
        // Less than 30 minutes left: take what is left, rounded up to a whole minute.
        assertEquals(10, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 600.0, new, 0.0))
        assertEquals(10, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 541.0, new, 0.0))
        assertEquals(1, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 20.0, new, 0.0))
        // Nothing left: nothing to take.
        assertEquals(0, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 0.0, new, 0.0))
    }

    @org.junit.Test
    fun anOlderPluginCanOnlyTakeBackExtraTime() {
        assertEquals(0, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 3600.0, 4, 0.0))
        assertEquals(15, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 3600.0, 4, 900.0))
        assertEquals(10, com.feerlessleadr.embidextrous.data.TakeAway.minutes(30, 600.0, 4, 3600.0))
    }
}
