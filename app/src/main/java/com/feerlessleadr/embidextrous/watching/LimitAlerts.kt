package com.feerlessleadr.embidextrous.watching

import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import java.time.Instant

/** What changed since the last look: who just reached a limit (alert them) and who is no longer over one (take their alert away). */
data class AlertChanges(val blocked: List<UserStatus>, val cleared: List<UserStatus>)

/**
 * Decides when to alert: a user who was allowed to watch and is now over a limit. The first update after starting only
 * records the current state (so opening the app never fires alerts for something that happened earlier), and a user whose
 * limit was already reached stays quiet until they have been allowed again.
 *
 * It also decides when an alert is out of date. Once someone is no longer over their limit (time was added from any phone or the dashboard, a
 * time-out ended, the limit was switched off) their alert must go, on every phone, so nobody adds the same time twice. On the first look every
 * user who is not over a limit counts as cleared, which removes an alert left over from before the service restarted. No Android classes: unit tested.
 */
class LimitAlerts {
    private val lastReason = HashMap<String, String>()

    /** Returns the users who just reached a limit. */
    @Synchronized
    fun newlyBlocked(cards: List<UserCard>, now: Instant = Instant.now()): List<UserStatus> = update(cards, now).blocked

    /** Looks at the current state and returns who to alert and whose alert to remove. */
    @Synchronized
    fun update(cards: List<UserCard>, now: Instant = Instant.now()): AlertChanges {
        val firstLook = lastReason.isEmpty()
        val blocked = ArrayList<UserStatus>()
        val cleared = ArrayList<UserStatus>()
        for (card in cards) {
            val user = card.status
            if (user.userId.isEmpty()) continue
            val reason = if (user.enabled) user.block(now) else "Allowed"
            val before = lastReason.put(user.userId, reason)
            if (!firstLook && before == "Allowed" && reason == "TimeLimitExceeded") blocked.add(user)
            if (reason != "TimeLimitExceeded" && (firstLook || before == "TimeLimitExceeded")) cleared.add(user)
        }
        return AlertChanges(blocked, cleared)
    }

    /** Forget everything (new server or new keys): the next update is a fresh start. */
    @Synchronized
    fun reset() = lastReason.clear()
}
