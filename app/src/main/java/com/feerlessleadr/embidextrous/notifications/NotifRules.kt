package com.feerlessleadr.embidextrous.notifications

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.floor

/** The Hall Monitor events a rule can watch for. */
@Serializable
enum class NotifKind {
    /** A user has used [NotifRule.percent] per cent of the limit that is closest to running out. */
    PercentUsed,

    /** A user has used all of their time (the limit was reached). Keeps the +15, +30 and +60 minute buttons. */
    AllTimeUsed,
}

/** One notification rule, stored on this phone. Hall Monitor applies to every limited user (per-user choice comes later). */
@Serializable
data class NotifRule(
    val id: String,
    val kind: NotifKind,
    val percent: Int = 0,
    val enabled: Boolean = true,
)

/** What the rules say when the default set is first created from the old Background alerts switch. */
const val LIMIT_REACHED_RULE_ID = "limit-reached"

object NotifRules {
    /** The first rule every phone gets: today's "limit reached" alert, switched on or off as the old setting was. */
    fun defaults(legacyAlertsOn: Boolean): List<NotifRule> =
        listOf(NotifRule(LIMIT_REACHED_RULE_ID, NotifKind.AllTimeUsed, enabled = legacyAlertsOn))

    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun encode(rules: List<NotifRule>): String = AppJson.encodeToString(ListSerializer(NotifRule.serializer()), rules)

    /** Null when the text is missing or broken, so the caller can fall back to the defaults. */
    fun decode(text: String?): List<NotifRule>? {
        if (text.isNullOrBlank()) return null
        return try {
            AppJson.decodeFromString(ListSerializer(NotifRule.serializer()), text)
        } catch (e: Exception) {
            null
        }
    }

    /** True when at least one rule is switched on, which is what keeps the background connection running. */
    fun anyEnabled(rules: List<NotifRule>): Boolean = rules.any { it.enabled }
}

/**
 * The percent used by the limit that is closest to running out, or null when the user has no limit or is switched off.
 * It is the percentage of that one period only: the plugin reports only the closest limit, so a weekly limit that is
 * further along than a daily one is not seen (the Settings text says so).
 */
fun percentUsed(status: UserStatus): Int? {
    if (!status.enabled) return null
    val allowed = status.allowedSeconds
    val remaining = status.remainingSeconds
    if (allowed <= 0.0 || remaining < 0.0) return null
    val used = (allowed - remaining).coerceIn(0.0, allowed)
    return floor(used / allowed * 100).toInt()
}

/** A percent rule that fired for one user. */
data class PercentFire(val rule: NotifRule, val user: UserStatus, val percent: Int)

/** [fires] to show now, and [state] to keep for the next update (replaces the old state). */
data class PercentResult(val fires: List<PercentFire>, val state: Map<String, String>)

/**
 * Decides which percent rules fire. A rule fires once per user, when the user reaches the percentage. It rearms when the
 * percentage drops below the rule, which happens at the limit's own reset, on a Reset from the app, or when time is added.
 * [state] maps "ruleId|userId" to the day it fired (kept for information only). On the first look after the app starts, anyone already over the rule is recorded
 * without firing, so opening the app never alerts for something that happened earlier. Pure Kotlin: unit tested.
 */
object PercentRules {
    fun evaluate(
        rules: List<NotifRule>,
        cards: List<UserCard>,
        state: Map<String, String>,
        today: String,
        firstLook: Boolean,
    ): PercentResult {
        val next = HashMap<String, String>()
        val fires = ArrayList<PercentFire>()
        for (rule in rules) {
            if (!rule.enabled || rule.kind != NotifKind.PercentUsed) continue
            for (card in cards) {
                val user = card.status
                if (user.userId.isEmpty()) continue
                val pct = percentUsed(user) ?: continue
                if (pct < rule.percent) continue
                val key = key(rule, user)
                // Fired before and still over the rule: stay quiet. Hall Monitor's day starts at the user's reset time (3 a.m. by
                // default), not at midnight, and a weekly or monthly limit stays high for days, so the calendar date must not rearm it.
                val alreadyFired = state.containsKey(key)
                next[key] = state[key] ?: today
                if (alreadyFired || firstLook) continue
                fires.add(PercentFire(rule, user, pct))
            }
        }
        return PercentResult(fires, next)
    }

    fun key(rule: NotifRule, user: UserStatus): String = "${rule.id}|${user.userId}"
}
