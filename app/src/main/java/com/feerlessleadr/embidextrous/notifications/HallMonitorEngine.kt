package com.feerlessleadr.embidextrous.notifications

import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import com.feerlessleadr.embidextrous.watching.AlertChanges
import com.feerlessleadr.embidextrous.watching.Link
import com.feerlessleadr.embidextrous.watching.LimitAlerts
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What one look at Hall Monitor's status produced: notifications to show, keys of notifications to remove, and the state to keep. */
data class HallMonitorResult(val deliveries: List<NotifDelivery>, val clears: List<String>, val state: Map<String, String>)

/**
 * Turns Hall Monitor's live status into notifications for the rules that are switched on. The plugin pushes the whole status, never "what changed",
 * so every event except the percentage is worked out by comparing this look with the last one. The first look after the service starts only records
 * (nothing fires for something that happened earlier). No Android classes: unit tested.
 */
class HallMonitorEngine(private val zone: ZoneId = ZoneId.systemDefault()) {
    private val limitAlerts = LimitAlerts()
    private val previous = HashMap<String, Seen>()
    private var firstLook = true
    private var problemSinceMs: Long? = null
    private var problemFired = false

    private data class Seen(val timedOut: Boolean, val playing: Boolean, val block: String, val creditSeconds: Double)

    @Synchronized
    fun update(
        rules: List<NotifRule>,
        cards: List<UserCard>,
        link: Link,
        nowMs: Long,
        state: Map<String, String>,
    ): HallMonitorResult {
        val now = Instant.ofEpochMilli(nowMs)
        val deliveries = ArrayList<NotifDelivery>()
        val clears = ArrayList<String>()
        val hm = rules.filter { it.enabled }
        fun rulesFor(id: String) = hm.filter { it.event == id }

        // "Used all of their time" and the removal of an alert that is out of date: the long-standing behavior, now per rule.
        val changes: AlertChanges = limitAlerts.update(cards, now)
        for (user in changes.cleared) clears.add(allUsedKey(user.userId))
        for (rule in rulesFor(NotifCatalog.Ids.HM_ALL_USED)) {
            for (user in changes.blocked) {
                if (!rule.appliesTo(user.userId)) continue
                val name = user.username.ifEmpty { "A user" }
                deliveries.add(
                    delivery(
                        NotifCatalog.Ids.HM_ALL_USED, rule, allUsedKey(user.userId),
                        "$name reached their ${user.periodLabel} limit",
                        "Used all ${span(user.allowedSeconds)} allowed ${WatchText.periodPhrase(user.periodLabel)}. Playback was stopped. Add 30 minutes to let them keep watching.",
                        addTimeFor = user.userId to name,
                    ),
                )
            }
        }

        // Percentages.
        val percent = PercentRules.evaluate(hm, cards, state, LocalDateTime.ofInstant(now, zone).toLocalDate().toString(), firstLook)
        // The percent result carries only percent keys; keep the daily-summary markers of rules that still exist.
        var nextState = percent.state + state.filterKeys { key -> key.startsWith("summary|") && hm.any { "summary|${it.id}" == key } }
        for (fire in percent.fires) {
            val name = fire.user.username.ifEmpty { "A user" }
            val used = fire.percent >= 100
            deliveries.add(
                delivery(
                    NotifCatalog.Ids.HM_PERCENT, fire.rule, PercentRules.key(fire.rule, fire.user, fire.period),
                    if (used) "$name has used all of their ${fire.period} time" else "$name has used ${fire.percent}% of their ${fire.period} time",
                    percentText(fire, used),
                ),
            )
        }

        // Changes between this look and the last.
        for (card in cards) {
            val user = card.status
            if (user.userId.isEmpty()) continue
            val name = user.username.ifEmpty { "A user" }
            val end = user.activeTimeOutEnd(now)
            val seen = Seen(end != null, user.nowPlaying != null, user.block(now), user.creditSecondsDaily)
            val before = previous.put(user.userId, seen)
            if (before == null || firstLook) continue
            if (!before.timedOut && seen.timedOut) {
                val until = end?.let { " until ${TIME.format(it.atZone(zone))}" }.orEmpty()
                fire(deliveries, rulesFor(NotifCatalog.Ids.HM_TIMEOUT_STARTED), user, NotifCatalog.Ids.HM_TIMEOUT_STARTED, "timeout|${user.userId}", "$name was timed out", "No playback$until.")
            }
            if (before.timedOut && !seen.timedOut) {
                fire(deliveries, rulesFor(NotifCatalog.Ids.HM_TIMEOUT_ENDED), user, NotifCatalog.Ids.HM_TIMEOUT_ENDED, "timeout|${user.userId}", "$name's time-out ended", "They can watch again.")
            }
            if (before.playing && seen.block == "OutsideTimeWindow" && before.block != "OutsideTimeWindow") {
                fire(deliveries, rulesFor(NotifCatalog.Ids.HM_OUTSIDE_HOURS), user, NotifCatalog.Ids.HM_OUTSIDE_HOURS, "hours|${user.userId}", "$name was stopped", "The allowed viewing hours ended.")
            }
            if (!before.playing && seen.playing) {
                val np = user.nowPlaying
                val what = listOfNotNull(np?.itemName?.takeIf { it.isNotEmpty() }, np?.deviceName?.takeIf { it.isNotEmpty() }?.let { "on $it" }).joinToString(" ")
                fire(deliveries, rulesFor(NotifCatalog.Ids.HM_STARTED), user, NotifCatalog.Ids.HM_STARTED, "started|${user.userId}", "$name started watching", what.ifEmpty { "Something started playing." })
            }
            val added = seen.creditSeconds - before.creditSeconds
            if (added >= 60.0) {
                fire(deliveries, rulesFor(NotifCatalog.Ids.HM_EXTRA_TIME), user, NotifCatalog.Ids.HM_EXTRA_TIME, "extra|${user.userId}", "$name was given more time", extraTimeText(user, added))
            }
        }
        // A user who is gone from the list no longer needs to be remembered.
        previous.keys.retainAll(cards.map { it.status.userId }.toSet())

        // Hall Monitor cannot be reached.
        val trouble = link == Link.OFFLINE || link == Link.KEY_REFUSED
        if (trouble) {
            val since = problemSinceMs ?: nowMs.also { problemSinceMs = it }
            for (rule in rulesFor(NotifCatalog.Ids.HM_CONNECTION)) {
                val minutes = rule.int("minutes", 10).coerceIn(1, 240)
                if (!problemFired && nowMs - since >= minutes * 60_000L) {
                    deliveries.add(
                        delivery(
                            NotifCatalog.Ids.HM_CONNECTION, rule, CONNECTION_KEY,
                            if (link == Link.KEY_REFUSED) "Hall Monitor refused the key" else "Hall Monitor cannot be reached",
                            "This phone has not heard from it for $minutes minutes.",
                        ),
                    )
                    problemFired = true
                }
            }
        } else if (link == Link.LIVE || link == Link.POLLING) {
            if (problemFired) clears.add(CONNECTION_KEY)
            problemSinceMs = null
            problemFired = false
        }

        // The daily summary: once a day, any time from the chosen time until three hours later.
        val local = LocalDateTime.ofInstant(now, zone)
        for (rule in rulesFor(NotifCatalog.Ids.HM_SUMMARY)) {
            val minute = parseTime(rule.text("time", "20:00")) ?: continue
            val nowMinute = local.hour * 60 + local.minute
            val marker = "summary|${rule.id}"
            val day = local.toLocalDate().toString()
            if (nowMinute >= minute && nowMinute < minute + 180 && nextState[marker] != day && cards.isNotEmpty() && link != Link.CONNECTING) {
                nextState = nextState + (marker to day)
                deliveries.add(delivery(NotifCatalog.Ids.HM_SUMMARY, rule, "summary", "Today's viewing", summaryText(cards)))
            }
        }

        firstLook = false
        return HallMonitorResult(deliveries, clears, nextState)
    }


    private fun fire(out: MutableList<NotifDelivery>, rules: List<NotifRule>, user: UserStatus, eventId: String, key: String, title: String, text: String) {
        for (rule in rules) if (rule.appliesTo(user.userId)) out.add(delivery(eventId, rule, key, title, text))
    }

    private fun delivery(eventId: String, rule: NotifRule, key: String, title: String, text: String, addTimeFor: Pair<String, String>? = null): NotifDelivery {
        val spec = NotifCatalog.spec(eventId)
        return NotifDelivery(
            NotifService.HallMonitor, rule.id, key, title, text,
            spec?.lockScreenText ?: "Hall Monitor has news. Unlock to see it.", spec?.opensTab, addTimeFor,
        )
    }

    companion object {
        const val CONNECTION_KEY = "connection"
        private val TIME = DateTimeFormatter.ofPattern("h:mm a")

        /** The notification key of the "used all" alert for a user. It is the user id so the +30 button and the clearing find it. */
        fun allUsedKey(userId: String): String = userId

        /** "20:00" as minutes after midnight, or null when the text is not a time. */
        fun parseTime(text: String): Int? {
            val parts = text.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }

        /** One line per user: what they watched today and, when they have a limit, what is left of it. */
        fun summaryText(cards: List<UserCard>): String {
            val rows = cards.map { it.status }.filter { it.enabled }.map { s ->
                val watched = s.watchedTodaySeconds ?: s.secondsWatchedDaily
                val left = s.remainingSeconds
                val name = s.username.ifEmpty { "A user" }
                if (left < 0) "$name: watched ${WatchText.total(watched)}, no limit" else "$name: watched ${WatchText.total(watched)}, ${span(left)} left ${WatchText.periodPhrase(s.periodLabel)}"
            }
            return if (rows.isEmpty()) "No one has a limit set." else rows.joinToString("\n")
        }

        /** "2 h", "1 h 36 min", "24 min", "45 s": whole hours drop the zero minutes. */
        fun span(seconds: Double): String {
            val total = maxOf(0.0, seconds).toLong()
            return when {
                total >= 3600 && total % 3600 == 0L -> "${total / 3600} h"
                total in 60..3599 && total % 60 == 0L -> "${total / 60} min"
                else -> WatchText.duration(seconds)
            }
        }

        /** The body of a percentage alert: how much was used of how much is allowed now (extra time included), and how much is left. */
        fun percentText(fire: PercentFire, used: Boolean): String {
            val limit = fire.limit
            val phrase = WatchText.periodPhrase(fire.period)
            if (limit == null || limit.allowedSeconds <= 0.0) {
                return if (used) "Playback is stopped until that time resets or you add more." else "Playback is still allowed."
            }
            val allowed = span(limit.allowedSeconds)
            return if (used) {
                "Used all $allowed allowed $phrase. Playback is stopped until that time resets or you add more."
            } else {
                "Used ${span(limit.usedSeconds)} of $allowed allowed $phrase. ${span(limit.remainingSeconds)} left."
            }
        }

        /** The body of an extra-time alert: what was added and what is left now. */
        fun extraTimeText(user: UserStatus, addedSeconds: Double): String {
            val left = user.remainingSeconds
            val tail = if (left >= 0) " ${user.username.ifEmpty { "This user" }} now has ${span(left)} left ${WatchText.periodPhrase(user.periodLabel)}." else ""
            return "${span(addedSeconds)} added.$tail"
        }
    }
}
