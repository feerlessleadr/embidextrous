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
