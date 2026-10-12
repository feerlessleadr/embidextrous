package com.feerlessleadr.embidextrous.watching

import com.feerlessleadr.embidextrous.data.MAX_MINUTES
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** Something on the Home screen that needs the person's attention. */
sealed interface Attention {
    val key: String

    /** The Hall Monitor key has not been entered on this phone. */
    data object NeedsKey : Attention {
        override val key = "needs-key"
    }

    /** The plugin refused the key (it is held back for a while, see [KeyGuard]). */
    data class KeyRefused(val detail: String) : Attention {
        override val key = "key-refused"
    }

    /** The server cannot be reached. */
    data class Offline(val detail: String) : Attention {
        override val key = "offline"
    }

    /** Someone has used up their time and playback is blocked. */
    data class AtLimit(val user: UserStatus) : Attention {
        override val key = "limit-${user.userId}"
    }
}

/** What the Home screen shows, worked out from the live data. No Android classes: unit tested. */
object HomeModel {
    /** Connection problems first (nothing else can be trusted), then everyone who is at their limit. A deliberate time-out is not a problem. */
    fun attention(cards: List<UserCard>, link: Link, detail: String, now: Instant): List<Attention> {
        val items = ArrayList<Attention>()
        when (link) {
            Link.NEEDS_KEY -> items.add(Attention.NeedsKey)
            Link.KEY_REFUSED -> items.add(Attention.KeyRefused(detail))
            Link.OFFLINE -> items.add(Attention.Offline(detail))
            else -> Unit
        }
        for (card in cards) {
            val s = card.status
            if (s.enabled && s.activeTimeOutEnd(now) == null && s.block(now) == "TimeLimitExceeded") items.add(Attention.AtLimit(s))
        }
        return items
    }

    fun watchingNow(cards: List<UserCard>): List<UserCard> = cards.filter { it.playing }

    /** Everyone with a running time-out. */
    fun timedOut(cards: List<UserCard>, now: Instant): List<UserCard> = cards.filter { it.status.activeTimeOutEnd(now) != null }

    /** People Bedtime can time out: a user whose limit is switched off cannot be timed out (the plugin refuses). */
    fun bedtimeCandidates(cards: List<UserCard>): List<UserStatus> = cards.map { it.status }.filter { it.enabled }
}

/** The two Bedtime choices: one hour, or until the next 6:00 in the morning. */
object Bedtime {
    const val ONE_HOUR_MINUTES = 60
    const val WAKE_HOUR = 6

    /** Whole minutes from [now] until the next [hour]:00 (so at 1 AM, until 6:00 the same morning; at 9 PM, until 6:00 tomorrow), within what the plugin accepts. */
    fun minutesUntilNext(hour: Int, now: ZonedDateTime): Int {
        var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val seconds = Duration.between(now, target).seconds
        return ((seconds + 59) / 60).toInt().coerceIn(1, MAX_MINUTES)
    }

    /** The moment a Bedtime of [minutes] from [now] ends. */
    fun endsAt(minutes: Int, now: ZonedDateTime): ZonedDateTime = now.plusMinutes(minutes.toLong())
}
