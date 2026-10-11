package com.feerlessleadr.embidextrous.watching

import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import java.time.Instant

/**
 * Decides when to alert: a user who was allowed to watch and is now over a limit. The first update after starting only
 * records the current state (so opening the app never fires alerts for something that happened earlier), and a user whose
 * limit was already reached stays quiet until they have been allowed again. No Android classes: unit tested.
 */
class LimitAlerts {
    private val lastReason = HashMap<String, String>()

    /** Returns the users who just reached a limit. */
    @Synchronized
    fun newlyBlocked(cards: List<UserCard>, now: Instant = Instant.now()): List<UserStatus> {
        val firstLook = lastReason.isEmpty()
        val result = ArrayList<UserStatus>()
        for (card in cards) {
            val user = card.status
            if (user.userId.isEmpty()) continue
            val reason = if (user.enabled) user.block(now) else "Allowed"
            val before = lastReason.put(user.userId, reason)
            if (!firstLook && before == "Allowed" && reason == "TimeLimitExceeded") result.add(user)
        }
        return result
    }

    /** Forget everything (new server or new keys): the next update is a fresh start. */
    @Synchronized
    fun reset() = lastReason.clear()
}
