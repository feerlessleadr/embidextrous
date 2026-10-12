package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.ListDefaults
import com.feerlessleadr.embidextrous.data.Templates
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.watching.LimitAlerts
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plugin's "defaults for new lists", as its configuration endpoint returns them. */
class ListDefaultsTest {
    private val config = """{"DefaultSortBy":"DateCreated","DefaultSortOrder":"Descending","DefaultListType":"Collection","DefaultMediaTypes":["Movie","Series"],
        "DefaultMakePublic":true,"DefaultMinItems":3,"DefaultMaxItems":400,"DefaultMaxPlayTimeMinutes":90,"DefaultAutoRefresh":"OnAllChanges",
        "DefaultScheduleTrigger":"Weekly","DefaultScheduleTime":"PT6H30M","DefaultScheduleDayOfWeek":"Friday","DefaultScheduleDayOfMonth":1,"DefaultScheduleMonth":1,"DefaultScheduleInterval":"PT15M",
        "ShowSmartListBadge":true}"""
    private val sorts = setOf("Name", "NoOrder", "DateCreated")

    @Test fun parsesEveryDefault() {
        val d = ListDefaults.parse(config)
        assertEquals("Collection", d.type)
        assertEquals(setOf("Movie", "Series"), d.mediaTypes)
        assertEquals(400, d.maxItems)
        assertEquals(90, d.maxPlayTimeMinutes)
        assertEquals(3, d.minItems)
        assertEquals("OnAllChanges", d.autoRefresh)
        assertEquals(true, d.isPublic)
        assertEquals("DateCreated", d.sortBy)
        assertTrue(d.sortDescending)
        val s = d.schedule!!
        assertEquals("Weekly", s.trigger)
        assertEquals("06:30", s.time)
        assertEquals(5, s.dayOfWeek)
    }

    @Test fun aBlankListGetsAllOfThem() {
        val draft = ListDefaults.parse(config).applyToBlank(Templates.draft("blank", "owner"), sorts)
        assertEquals("Collection", draft.type)
        assertEquals(setOf("Movie", "Series"), draft.mediaTypes)
        assertEquals("400", draft.maxItems)
        assertEquals("90", draft.maxPlayTimeMinutes)
        assertEquals("3", draft.minItems)
        assertEquals("OnAllChanges", draft.autoRefresh)
        assertTrue(draft.isPublic)
        assertEquals("DateCreated", draft.sorts.single().sortBy)
        assertTrue(draft.sorts.single().descending)
        assertEquals(1, draft.schedules.size)
    }

    @Test fun aTemplateKeepsWhatItDecidesAndGetsTheRest() {
        val next = Templates.draft("next-up", "owner")
        val draft = ListDefaults.parse(config).applyToTemplate(next)
        assertEquals("Playlist", draft.type) // the template's own type, not the default
        assertEquals(next.mediaTypes, draft.mediaTypes)
        assertEquals(next.sorts.single().sortBy, draft.sorts.single().sortBy)
        assertEquals(next.autoRefresh, draft.autoRefresh)
        assertEquals("400", draft.maxItems) // but the limits and the schedule are the plugin's defaults
        assertEquals("3", draft.minItems)
        assertEquals(1, draft.schedules.size)
    }

    @Test fun zeroMeansNoLimitAndAnUnknownSortIsIgnored() {
        val d = ListDefaults.parse("""{"DefaultMaxItems":0,"DefaultMaxPlayTimeMinutes":0,"DefaultMinItems":0,"DefaultSortBy":"SomethingNew"}""")
        val draft = d.applyToBlank(Templates.draft("blank", "owner"), sorts)
        assertEquals("", draft.maxItems)
        assertEquals("", draft.minItems)
        assertEquals("Name", draft.sorts.single().sortBy) // the app's own starting sort stays
    }

    @Test fun missingOrBrokenConfigurationChangesNothing() {
        val start = Templates.draft("blank", "owner")
        assertEquals(start.maxItems, ListDefaults.parse("not json").applyToBlank(start, sorts).maxItems)
        assertEquals(start.type, ListDefaults.parse("{}").applyToBlank(start, sorts).type)
        // the plugin's page treats a missing minimum as 1
        assertEquals("1", ListDefaults.parse("{}").applyToBlank(start, sorts).minItems)
        assertNull(ListDefaults.parse("""{"DefaultScheduleTrigger":"Sometimes"}""").schedule)
    }

    @Test fun durationsAndTimeSpansAreRead() {
        assertEquals(900L, ListDefaults.isoSeconds("PT15M"))
        assertEquals(5400L, ListDefaults.isoSeconds("PT1H30M"))
        assertEquals(90_000L, ListDefaults.isoSeconds("P1DT1H"))
        assertEquals(0L, ListDefaults.isoSeconds("PT0S"))
        assertEquals(900L, ListDefaults.isoSeconds("00:15:00"))
        assertEquals(93_600L, ListDefaults.isoSeconds("1.02:00:00"))
        assertNull(ListDefaults.isoSeconds("soon"))
        assertEquals("00:15:00", ListDefaults.timeSpan(900))
        assertEquals("1.02:00:00", ListDefaults.timeSpan(93_600))
        assertEquals("00:15:00", ListDefaults.parse("""{"DefaultScheduleTrigger":"Interval","DefaultScheduleInterval":"PT15M"}""").schedule!!.interval)
    }
}

/** An alert must disappear on every phone as soon as the person is no longer over their limit. */
class AlertClearingTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    private fun card(reason: String, id: String = "u1", enabled: Boolean = true) =
        UserCard(UserStatus(userId = id, username = "Sam", blockReason = reason, isEnabled = enabled), 0L)

    @Test fun timeAddedElsewhereClearsTheAlert() {
        val alerts = LimitAlerts()
        alerts.update(listOf(card("Allowed")), now)
        assertEquals(listOf("u1"), alerts.update(listOf(card("TimeLimitExceeded")), now).blocked.map { it.userId })
        // someone adds time (on another phone, or in the dashboard): this phone's alert must go
        val changes = alerts.update(listOf(card("Allowed")), now)
        assertEquals(listOf("u1"), changes.cleared.map { it.userId })
        assertTrue(changes.blocked.isEmpty())
        // and it is only cleared once
        assertTrue(alerts.update(listOf(card("Allowed")), now).cleared.isEmpty())
    }

    @Test fun stillBlockedKeepsTheAlert() {
        val alerts = LimitAlerts()
        alerts.update(listOf(card("Allowed")), now)
        alerts.update(listOf(card("TimeLimitExceeded")), now)
        val again = alerts.update(listOf(card("TimeLimitExceeded")), now)
        assertTrue(again.cleared.isEmpty())
        assertTrue(again.blocked.isEmpty())
    }

    @Test fun aSwitchedOffLimitAlsoClearsIt() {
        val alerts = LimitAlerts()
        alerts.update(listOf(card("Allowed")), now)
        alerts.update(listOf(card("TimeLimitExceeded")), now)
        assertEquals(listOf("u1"), alerts.update(listOf(card("TimeLimitExceeded", enabled = false)), now).cleared.map { it.userId })
    }

    @Test fun theFirstLookClearsLeftoverAlertsOfAllowedUsersOnly() {
        val alerts = LimitAlerts()
        val first = alerts.update(listOf(card("Allowed", "a"), card("TimeLimitExceeded", "b")), now)
        assertEquals(listOf("a"), first.cleared.map { it.userId })
        assertTrue(first.blocked.isEmpty())
    }
}
