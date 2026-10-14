package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.NowPlaying
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.notifications.HallMonitorEngine
import com.feerlessleadr.embidextrous.notifications.NotifCatalog
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.watching.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HallMonitorEngineTest {
    private val engine = HallMonitorEngine(ZoneOffset.UTC)
    private val t0 = Instant.parse("2026-10-14T18:00:00Z").toEpochMilli()

    private fun rule(event: String, vararg params: Pair<String, String>, users: List<String> = emptyList()) =
        NotifRule("r-$event", event, true, mapOf(*params), users)

    private fun user(
        id: String = "u1",
        name: String = "Alex",
        block: String? = "Allowed",
        timeOutUntil: String? = null,
        playing: Boolean = false,
        credit: Double = 0.0,
        watched: Double = 0.0,
    ) = UserStatus(
        userId = id, username = name, isEnabled = true, blockReason = block, timeOutUntil = timeOutUntil, creditSecondsDaily = credit,
        secondsWatchedDaily = watched, secondsRemaining = 3600.0, limitingAllowedSeconds = 7200.0, limitingPeriod = "Daily", apiLevel = 4,
        nowPlaying = if (playing) NowPlaying(itemName = "Bluey", deviceName = "Tablet") else null,
    )

    private fun cards(vararg users: UserStatus) = users.map { UserCard(it, 0L) }

    private fun run(rules: List<NotifRule>, c: List<UserCard>, link: Link = Link.LIVE, atMs: Long = t0, state: Map<String, String> = emptyMap()) =
        engine.update(rules, c, link, atMs, state)

    @Test
    fun aTimeOutStartingAndEndingAreEachAnnouncedAfterTheFirstLook() {
        val rules = listOf(rule(NotifCatalog.Ids.HM_TIMEOUT_STARTED), rule(NotifCatalog.Ids.HM_TIMEOUT_ENDED))
        // The first look only records, even for someone already timed out.
        assertTrue(run(rules, cards(user(timeOutUntil = "2026-10-14T20:00:00Z"))).deliveries.isEmpty())
        assertTrue(run(rules, cards(user(timeOutUntil = "2026-10-14T20:00:00Z"))).deliveries.isEmpty())
        val ended = run(rules, cards(user(timeOutUntil = null))).deliveries.single()
        assertEquals("Alex's time-out ended", ended.title)

        val started = run(rules, cards(user(timeOutUntil = "2026-10-14T20:00:00Z"))).deliveries.single()
        assertEquals("Alex was timed out", started.title)
        assertTrue(started.text.contains("6:00 PM") || started.text.contains("8:00 PM"))
        assertEquals("A time-out started. Unlock to see who.", started.lockScreenText)
    }

    @Test
    fun startedWatchingNamesWhatIsPlayingAndHonorsChosenUsers() {
        val everyone = listOf(rule(NotifCatalog.Ids.HM_STARTED))
        run(everyone, cards(user(playing = false)))
        val d = run(everyone, cards(user(playing = true))).deliveries.single()
        assertEquals("Alex started watching", d.title)
        assertEquals("Bluey on Tablet", d.text)

        val onlySam = listOf(rule(NotifCatalog.Ids.HM_STARTED, users = listOf("u2")))
        val engine2 = HallMonitorEngine(ZoneOffset.UTC)
        engine2.update(onlySam, cards(user(playing = false)), Link.LIVE, t0, emptyMap())
        assertTrue(engine2.update(onlySam, cards(user(playing = true)), Link.LIVE, t0, emptyMap()).deliveries.isEmpty())
    }

    @Test
    fun playbackStoppedWhenTheAllowedHoursEndIsAnnouncedOnlyIfSomeoneWasWatching() {
        val rules = listOf(rule(NotifCatalog.Ids.HM_OUTSIDE_HOURS))
        run(rules, cards(user(playing = true)))
        val d = run(rules, cards(user(block = "OutsideTimeWindow", playing = false))).deliveries.single()
        assertEquals("Alex was stopped", d.title)
        // Everyone flips to the blocked state at the end of the hours; someone who was not watching is not announced.
        val quiet = HallMonitorEngine(ZoneOffset.UTC)
        quiet.update(rules, cards(user(playing = false)), Link.LIVE, t0, emptyMap())
        assertTrue(quiet.update(rules, cards(user(block = "OutsideTimeWindow")), Link.LIVE, t0, emptyMap()).deliveries.isEmpty())
    }

    @Test
    fun extraTimeNeedsAtLeastAMinuteMore() {
        val rules = listOf(rule(NotifCatalog.Ids.HM_EXTRA_TIME))
        run(rules, cards(user(credit = 0.0)))
        assertTrue(run(rules, cards(user(credit = 30.0))).deliveries.isEmpty())
        val d = run(rules, cards(user(credit = 930.0))).deliveries.single()
        assertEquals("Alex was given more time", d.title)
        assertEquals("15 min added to today's time.", d.text)
    }

    @Test
    fun usedAllTimeKeepsItsAddTimeButtonsAndIsClearedWhenTimeIsAdded() {
        val rules = listOf(rule(NotifCatalog.Ids.HM_ALL_USED))
        run(rules, cards(user(block = "Allowed")))
        val blocked = run(rules, cards(user(block = "TimeLimitExceeded")))
        val d = blocked.deliveries.single()
        assertEquals("Alex reached their daily limit", d.title)
        assertEquals("u1" to "Alex", d.addTimeFor)
        assertEquals(HallMonitorEngine.allUsedKey("u1"), d.key)
        val allowed = run(rules, cards(user(block = "Allowed")))
        assertEquals(listOf(HallMonitorEngine.allUsedKey("u1")), allowed.clears)
    }

    @Test
    fun aConnectionProblemIsAnnouncedOnceAfterTheChosenMinutesAndClearedWhenBack() {
        val rules = listOf(rule(NotifCatalog.Ids.HM_CONNECTION, "minutes" to "10"))
        val c = cards(user())
        assertTrue(run(rules, c, Link.OFFLINE, t0).deliveries.isEmpty())
        assertTrue(run(rules, c, Link.OFFLINE, t0 + 5 * 60_000L).deliveries.isEmpty())
        val d = run(rules, c, Link.OFFLINE, t0 + 11 * 60_000L).deliveries.single()
        assertEquals("Hall Monitor cannot be reached", d.title)
        assertTrue(run(rules, c, Link.OFFLINE, t0 + 20 * 60_000L).deliveries.isEmpty())
        assertEquals(listOf(HallMonitorEngine.CONNECTION_KEY), run(rules, c, Link.LIVE, t0 + 21 * 60_000L).clears)
    }

    @Test
    fun theDailySummaryIsSentOncePerDayFromTheChosenTime() {
        val rules = listOf(rule(NotifCatalog.Ids.HM_SUMMARY, "time" to "20:00"))
        val c = cards(user(watched = 1800.0), user(id = "u2", name = "Sam", watched = 600.0))
        val eight = Instant.parse("2026-10-14T20:05:00Z").toEpochMilli()
        assertTrue(run(rules, c, atMs = Instant.parse("2026-10-14T19:00:00Z").toEpochMilli()).deliveries.isEmpty())
        val sent = run(rules, c, atMs = eight)
        val d = sent.deliveries.single()
        assertEquals("Today's viewing", d.title)
        assertEquals("Alex: 30 min | Sam: 10 min", d.text)
        // Not again that day, even with the state kept; the next evening it is sent again.
        assertTrue(run(rules, c, atMs = eight + 60_000L, state = sent.state).deliveries.isEmpty())
        assertEquals(1, run(rules, c, atMs = eight + 24 * 3_600_000L, state = sent.state).deliveries.size)
        // Too late (more than three hours after the chosen time) it is skipped for that day.
        assertTrue(HallMonitorEngine(ZoneOffset.UTC).update(rules, c, Link.LIVE, Instant.parse("2026-10-15T23:30:00Z").toEpochMilli(), emptyMap()).deliveries.isEmpty())
    }

    @Test
    fun percentRulesKeepTheirBaselineAndPeriodChoice() {
        val weekly = rule(NotifCatalog.Ids.HM_PERCENT, "percent" to "80", "period" to "weekly")
        val over = UserStatus(
            userId = "u1", username = "Alex", isEnabled = true, apiLevel = 4,
            limits = listOf(
                com.feerlessleadr.embidextrous.data.LimitStatus("Daily", 100.0, 90.0, 10.0, 90),
                com.feerlessleadr.embidextrous.data.LimitStatus("Weekly", 100.0, 85.0, 15.0, 85),
            ),
        )
        val first = run(listOf(weekly), cards(over))
        assertTrue(first.deliveries.isEmpty()) // baseline
        // A new look at the same state stays quiet; the daily limit is not what this rule watches.
        assertTrue(run(listOf(weekly), cards(over), state = first.state).deliveries.isEmpty())
        assertNull(first.state.keys.firstOrNull { it.endsWith("|daily") })
        assertFalse(first.state.keys.none { it.endsWith("|weekly") })
    }

    @Test
    fun theTimeTextIsReadStrictly() {
        assertEquals(20 * 60, HallMonitorEngine.parseTime("20:00"))
        assertEquals(7 * 60 + 5, HallMonitorEngine.parseTime(" 7:05 "))
        assertNull(HallMonitorEngine.parseTime("25:00"))
        assertNull(HallMonitorEngine.parseTime("8pm"))
    }
}
