package com.feerlessleadr.embidextrous.watching

import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One line of text per user for the home-screen widget. No Android classes: unit tested. */
object WidgetText {
    /** The widget's headline, the same idea as the Home screen's status card: all good, or who is at their limit. [hideNames] shows "User 1" instead of names. */
    fun summary(users: List<UserStatus>, now: Instant, hideNames: Boolean = false): Summary {
        val atLimit = users.withIndex().filter { (_, u) -> u.enabled && u.activeTimeOutEnd(now) == null && u.block(now) == "TimeLimitExceeded" }
            .map { (i, u) -> if (hideNames) "User ${i + 1}" else u.username.ifEmpty { "Unknown" } }
        if (atLimit.isNotEmpty()) {
            val who = if (atLimit.size <= 2) atLimit.joinToString(" and ") else "${atLimit.size} people"
            return Summary("$who at the limit", needsAttention = true)
        }
        val watching = users.count { it.nowPlaying != null }
        return Summary(if (watching == 0) "All good" else if (watching == 1) "All good · 1 watching" else "All good · $watching watching", needsAttention = false)
    }

    data class Summary(val text: String, val needsAttention: Boolean)

    /** With a non-null [hiddenLabel] the user is shown under that label ("User 1") instead of by name. */
    fun row(user: UserStatus, now: Instant, zone: ZoneId, locale: Locale, hiddenLabel: String? = null): String {
        val name = hiddenLabel ?: user.username.ifEmpty { "Unknown" }
        if (!user.enabled) return "$name — limit off"
        val timeOutEnd = user.activeTimeOutEnd(now)
        if (timeOutEnd != null) {
            val text = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(timeOutEnd)
            return "$name — timed out until $text"
        }
        when (user.blockReason) {
            "TimeLimitExceeded" -> return "$name — limit reached"
            "OutsideTimeWindow" -> return "$name — outside allowed hours"
        }
        val remaining = user.remainingSeconds
        if (remaining < 0) return "$name — no limit"
        return "$name — ${WatchText.durationLeft(remaining)} left"
    }
}
