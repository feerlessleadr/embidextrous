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

    @Test fun widgetRowCanHideTheName() {
        assertEquals("User 2 — 1 h 00 min left", WidgetText.row(user(name = "Secret"), now, zone, Locale.US, "User 2"))
    }
}
