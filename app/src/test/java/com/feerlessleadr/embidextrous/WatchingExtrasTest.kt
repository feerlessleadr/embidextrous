package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.watching.LimitAlerts
import com.feerlessleadr.embidextrous.watching.WidgetText
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class WatchingExtrasTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val zone = ZoneId.of("UTC")

    private fun user(
        id: String = "u1",
        name: String = "Alex",
        reason: String? = "Allowed",
        remaining: Double? = 3600.0,
        enabled: Boolean? = true,
        timeOut: String? = null,
    ) = UserStatus(userId = id, username = name, blockReason = reason, secondsRemaining = remaining, isEnabled = enabled, timeOutUntil = timeOut)

    private fun cards(vararg users: UserStatus) = users.map { UserCard(it, 0L) }

    // ---- alerts ---------------------------------------------------------------------------------------------

    @Test fun firstLookNeverAlerts() {
        val alerts = LimitAlerts()
        assertTrue(alerts.newlyBlocked(cards(user(reason = "TimeLimitExceeded")), now).isEmpty())
    }

    @Test fun alertsWhenAllowedBecomesLimitReached() {
        val alerts = LimitAlerts()
        alerts.newlyBlocked(cards(user()), now)
        val fired = alerts.newlyBlocked(cards(user(reason = "TimeLimitExceeded", remaining = 0.0)), now)
        assertEquals(listOf("u1"), fired.map { it.userId })
    }

    @Test fun staysQuietWhileStillBlockedAndAlertsAgainAfterBeingAllowed() {
        val alerts = LimitAlerts()
        alerts.newlyBlocked(cards(user()), now)
        alerts.newlyBlocked(cards(user(reason = "TimeLimitExceeded")), now)
        assertTrue(alerts.newlyBlocked(cards(user(reason = "TimeLimitExceeded")), now).isEmpty())
        alerts.newlyBlocked(cards(user()), now)
        assertEquals(1, alerts.newlyBlocked(cards(user(reason = "TimeLimitExceeded")), now).size)
    }

    @Test fun aTimeOutIsNotALimitReached() {
        val alerts = LimitAlerts()
        alerts.newlyBlocked(cards(user()), now)
        assertTrue(alerts.newlyBlocked(cards(user(reason = "TimedOut")), now).isEmpty())
    }

    @Test fun resetMakesTheNextUpdateAFreshStart() {
        val alerts = LimitAlerts()
        alerts.newlyBlocked(cards(user()), now)
        alerts.reset()
        assertTrue(alerts.newlyBlocked(cards(user(reason = "TimeLimitExceeded")), now).isEmpty())
    }

    // ---- widget text ----------------------------------------------------------------------------------------

    @Test fun widgetRowShowsTimeLeft() {
        assertEquals("Alex — 1 h 00 min left", WidgetText.row(user(), now, zone, Locale.US))
    }

    @Test fun widgetRowStates() {
        assertEquals("Alex — limit reached", WidgetText.row(user(reason = "TimeLimitExceeded", remaining = 0.0), now, zone, Locale.US))
        assertEquals("Alex — outside allowed hours", WidgetText.row(user(reason = "OutsideTimeWindow"), now, zone, Locale.US))
        assertEquals("Alex — limit off", WidgetText.row(user(enabled = false), now, zone, Locale.US))
        assertEquals("Alex — no limit", WidgetText.row(user(remaining = -1.0), now, zone, Locale.US))
    }

    @Test fun widgetRowShowsTimeOutAndIgnoresAnExpiredOne() {
        val active = WidgetText.row(user(reason = "TimedOut", timeOut = "2026-10-10T15:00:00Z"), now, zone, Locale.US)
        assertTrue(active, active.startsWith("Alex — timed out until "))
        val expired = WidgetText.row(user(timeOut = "2026-10-10T09:00:00Z"), now, zone, Locale.US)
        assertEquals("Alex — 1 h 00 min left", expired)
    }

    // ---- protecting the plugin from its own lockout ---------------------------------------------------------

    @Test fun keyGuardHoldsBackForFiveMinutesAfterARefusal() {
        val guard = com.feerlessleadr.embidextrous.watching.KeyGuard()
        val t0 = 1_000_000L
        assertTrue(!guard.isBlocked(t0))
        guard.refused(t0)
        assertTrue(guard.isBlocked(t0 + 1))
        assertTrue(guard.isBlocked(t0 + 299_999))
        assertTrue(!guard.isBlocked(t0 + 300_000))
    }

    @Test fun keyGuardResetsWhenANewKeyIsSaved() {
        val guard = com.feerlessleadr.embidextrous.watching.KeyGuard()
        guard.refused(0L)
        guard.reset()
        assertTrue(!guard.isBlocked(1L))
    }

    @Test fun keyGuardSaysHowManyMinutesAreLeft() {
        val guard = com.feerlessleadr.embidextrous.watching.KeyGuard()
        guard.refused(0L)
        assertEquals(5L, guard.minutesLeft(1L))
        assertEquals(1L, guard.minutesLeft(299_000L))
    }

    @Test fun anAttemptEveryFiveMinutesNeverReachesThePluginsLockoutOfFiveInFive() {
        // The plugin locks after 5 wrong keys within 5 minutes; with the guard a wrong key is retried at most once per BLOCK_MS.
        assertTrue(com.feerlessleadr.embidextrous.watching.KeyGuard.BLOCK_MS >= 5 * 60_000L)
    }

    // ---- time left rounds up ----------------------------------------------------------------------------------

    @Test fun timeLeftRoundsUpFromTenMinutes() {
        val w = com.feerlessleadr.embidextrous.data.WatchText
        // 15 minutes just added to someone who ran 0.4 s past their limit: 14 min 59.6 s must read 15 min.
        assertEquals("15 min", w.durationLeft(899.6))
        assertEquals("15 min", w.durationLeft(900.0))
        assertEquals("16 min", w.durationLeft(900.4))
        assertEquals("10 min", w.durationLeft(600.0))
        assertEquals("11 min", w.durationLeft(600.5))
        assertEquals("1 h 15 min", w.durationLeft(4499.6))
        assertEquals("2 h 00 min", w.durationLeft(7140.5))
    }

    @Test fun timeLeftBelowTenMinutesStillCountsSeconds() {
        val w = com.feerlessleadr.embidextrous.data.WatchText
        assertEquals("9 min 59 s", w.durationLeft(599.9))
        assertEquals("45 s", w.durationLeft(45.9))
        assertEquals("0 s", w.durationLeft(-3.0))
    }

    @Test fun timeUsedKeepsRoundingDown() {
        assertEquals("14 min", com.feerlessleadr.embidextrous.data.WatchText.duration(899.6))
    }

    @Test fun theCountdownPassesTenMinutesWithoutAJump() {
        val w = com.feerlessleadr.embidextrous.data.WatchText
        // Counting down second by second: 11 min holds until 10:00 exactly, then 10 min, then 9 min 59 s.
        assertEquals("11 min", w.durationLeft(660.0))
        assertEquals("11 min", w.durationLeft(601.0))
        assertEquals("10 min", w.durationLeft(600.0))
        assertEquals("9 min 59 s", w.durationLeft(599.0))
    }

    // ---- Home: what needs attention ---------------------------------------------------------------------------

    private fun playingUser(id: String = "u1", name: String = "Alex") =
        UserStatus(userId = id, username = name, blockReason = "Allowed", secondsRemaining = 3600.0, isEnabled = true,
            nowPlaying = com.feerlessleadr.embidextrous.data.NowPlaying(itemId = "i1", itemName = "A film"))

    @Test fun nothingNeedsAttentionWhenEveryoneHasTimeLeft() {
        val items = com.feerlessleadr.embidextrous.watching.HomeModel.attention(cards(user(), user("u2", "Sam")), com.feerlessleadr.embidextrous.watching.Link.LIVE, "", now)
        assertTrue(items.isEmpty())
    }

    @Test fun someoneAtTheirLimitNeedsAttention() {
        val items = com.feerlessleadr.embidextrous.watching.HomeModel.attention(
            cards(user(reason = "TimeLimitExceeded", remaining = 0.0)), com.feerlessleadr.embidextrous.watching.Link.LIVE, "", now,
        )
        assertEquals(listOf("limit-u1"), items.map { it.key })
    }

    @Test fun aDeliberateTimeOutOrASwitchedOffLimitIsNotAProblem() {
        val timedOut = user(reason = "TimedOut", timeOut = "2026-10-10T15:00:00Z")
        val off = user("u2", "Sam", reason = "TimeLimitExceeded", enabled = false)
        val items = com.feerlessleadr.embidextrous.watching.HomeModel.attention(cards(timedOut, off), com.feerlessleadr.embidextrous.watching.Link.LIVE, "", now)
        assertTrue(items.isEmpty())
    }

    @Test fun connectionProblemsComeFirst() {
        val items = com.feerlessleadr.embidextrous.watching.HomeModel.attention(
            cards(user(reason = "TimeLimitExceeded", remaining = 0.0)), com.feerlessleadr.embidextrous.watching.Link.KEY_REFUSED, "no", now,
        )
        assertEquals(listOf("key-refused", "limit-u1"), items.map { it.key })
        val offline = com.feerlessleadr.embidextrous.watching.HomeModel.attention(emptyList(), com.feerlessleadr.embidextrous.watching.Link.OFFLINE, "x", now)
        assertEquals(listOf("offline"), offline.map { it.key })
        val noKey = com.feerlessleadr.embidextrous.watching.HomeModel.attention(emptyList(), com.feerlessleadr.embidextrous.watching.Link.NEEDS_KEY, "", now)
        assertEquals(listOf("needs-key"), noKey.map { it.key })
    }

    @Test fun pollingAndConnectingAreNotProblems() {
        for (link in listOf(com.feerlessleadr.embidextrous.watching.Link.POLLING, com.feerlessleadr.embidextrous.watching.Link.CONNECTING)) {
            assertTrue(com.feerlessleadr.embidextrous.watching.HomeModel.attention(cards(user()), link, "", now).isEmpty())
        }
    }

    @Test fun watchingNowListsOnlyWhoIsPlaying() {
        val all = cards(playingUser(), user("u2", "Sam"))
        assertEquals(listOf("u1"), com.feerlessleadr.embidextrous.watching.HomeModel.watchingNow(all).map { it.status.userId })
    }

    @Test fun bedtimeOffersOnlyUsersWhoseLimitIsOn() {
        val all = cards(user(), user("u2", "Sam", enabled = false))
        assertEquals(listOf("u1"), com.feerlessleadr.embidextrous.watching.HomeModel.bedtimeCandidates(all).map { it.userId })
    }

    // ---- Bedtime: how long until 6:00 -------------------------------------------------------------------------

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0, zone: String = "UTC") =
        java.time.ZonedDateTime.of(year, month, day, hour, minute, second, 0, java.time.ZoneId.of(zone))

    @Test fun eveningBedtimeRunsToTomorrowMorning() {
        assertEquals(9 * 60, com.feerlessleadr.embidextrous.watching.Bedtime.minutesUntilNext(6, at(2026, 10, 10, 21, 0)))
    }

    @Test fun bedtimeAfterMidnightRunsToThisMorning() {
        assertEquals(5 * 60, com.feerlessleadr.embidextrous.watching.Bedtime.minutesUntilNext(6, at(2026, 10, 11, 1, 0)))
    }

    @Test fun atExactlySixTheNextSixIsTomorrow() {
        assertEquals(24 * 60, com.feerlessleadr.embidextrous.watching.Bedtime.minutesUntilNext(6, at(2026, 10, 11, 6, 0)))
    }

    @Test fun aPartialMinuteRoundsUp() {
        assertEquals(1, com.feerlessleadr.embidextrous.watching.Bedtime.minutesUntilNext(6, at(2026, 10, 11, 5, 59, 30)))
    }

    @Test fun theNightTheClocksSpringForwardIsOneHourShorter() {
        // US clocks jump from 2:00 to 3:00 on 2026-03-08: 9 PM to 6 AM is 8 real hours, not 9.
        assertEquals(8 * 60, com.feerlessleadr.embidextrous.watching.Bedtime.minutesUntilNext(6, at(2026, 3, 7, 21, 0, zone = "America/New_York")))
    }

    @Test fun bedtimeEndsWhereItSays() {
        val start = at(2026, 10, 10, 21, 0)
        val end = com.feerlessleadr.embidextrous.watching.Bedtime.endsAt(60, start)
        assertEquals(22, end.hour)
    }

    // ---- Profiles ---------------------------------------------------------------------------------------------

    @Test fun parentNeverSeesSmartLists() {
        assertTrue(!com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(com.feerlessleadr.embidextrous.data.AppProfile.Parent, true, true))
    }

    @Test fun administratorSeesSmartListsOnlyWithAnAdministratorAccount() {
        val p = com.feerlessleadr.embidextrous.data.AppProfile.Administrator
        assertTrue(com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(p, true, false))
        assertTrue(!com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(p, false, false))
    }

    @Test fun customFollowsItsOwnSwitch() {
        val p = com.feerlessleadr.embidextrous.data.AppProfile.Custom
        assertTrue(com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(p, true, true))
        assertTrue(!com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(p, true, false))
    }

    @Test fun anUnchosenProfileBehavesLikeAdministrator() {
        assertTrue(com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(null, true, false))
        assertTrue(!com.feerlessleadr.embidextrous.data.Profiles.showsSmartLists(null, false, false))
        assertEquals(com.feerlessleadr.embidextrous.data.AppProfile.Parent, com.feerlessleadr.embidextrous.data.AppProfile.fromName("Parent"))
        assertEquals(null, com.feerlessleadr.embidextrous.data.AppProfile.fromName("nonsense"))
    }

    @Test fun widgetRowCanHideTheName() {
        assertEquals("User 2 — 1 h 00 min left", WidgetText.row(user(name = "Secret"), now, zone, Locale.US, "User 2"))
    }
}
