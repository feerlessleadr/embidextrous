package com.feerlessleadr.embidextrous.notifications

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.floor

/** What the rules said before 0.10: the first rule is the old Background alerts switch, which became "used all of their time". */
const val LIMIT_REACHED_RULE_ID = "limit-reached"

/** Storage and bookkeeping for the rule list. Pure Kotlin, unit tested. */
object NotifRules {
    fun newId(): String = java.util.UUID.randomUUID().toString()

    fun encode(rules: List<NotifRule>): String = AppJson.encodeToString(ListSerializer(NotifRule.serializer()), rules)

    /** Null when the text is missing or broken. */
    fun decode(text: String?): List<NotifRule>? {
        if (text.isNullOrBlank()) return null
        return try {
            AppJson.decodeFromString(ListSerializer(NotifRule.serializer()), text)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads the list kept by 0.9 (rules with a "kind" and a "percent") as new rules, keeping their ids so the "already fired" state carries over.
     * Null when the text is not that list.
     */
    fun migrateLegacy(text: String?): List<NotifRule>? {
        if (text.isNullOrBlank()) return null
        val array = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return null
        val rules = array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val enabled = (o["enabled"] as? JsonPrimitive)?.contentOrNull != "false"
            when ((o["kind"] as? JsonPrimitive)?.contentOrNull) {
                "PercentUsed" -> NotifRule(
                    id, NotifCatalog.Ids.HM_PERCENT, enabled,
                    mapOf("percent" to ((o["percent"] as? JsonPrimitive)?.intOrNull ?: 80).toString(), "period" to "any"),
                )
                "AllTimeUsed" -> NotifRule(id, NotifCatalog.Ids.HM_ALL_USED, enabled)
                else -> null
            }
        }
        return rules
    }

    /**
     * Adds the default rule of every alert type this phone has not seen before. [seeded] is the set of alert ids already given a default, so a rule the
     * person deleted does not come back. Returns the new list and the new set. An alert type that already has a rule (for example one carried over from 0.9) is not given another.
     */
    fun seed(rules: List<NotifRule>, specs: List<EventSpec>, seeded: Set<String>): Pair<List<NotifRule>, Set<String>> {
        val outRules = rules.toMutableList()
        val outSeeded = seeded.toMutableSet()
        for (spec in specs) {
            if (spec.id in outSeeded) continue
            outSeeded.add(spec.id)
            if (outRules.any { it.event == spec.id }) continue
            outRules.add(NotifRule(newId(), spec.id, spec.defaultOn, spec.defaultParams()))
        }
        return outRules to outSeeded
    }

    /** The rules that may run now: switched on, and belonging to a service that is available to this phone and switched on. */
    fun active(rules: List<NotifRule>, services: Set<NotifService>): List<NotifRule> =
        rules.filter { it.enabled && NotifCatalog.spec(it.event)?.service in services }

    /** The services that need a connection: those with at least one active rule. */
    fun servicesInUse(rules: List<NotifRule>, services: Set<NotifService>): Set<NotifService> =
        active(rules, services).mapNotNull { NotifCatalog.spec(it.event)?.service }.toSet()
}

/**
 * The percent used by the limit that is closest to running out, or null when the user has no limit or is switched off. It is the percentage of
 * that one period only: an older plugin reports only the closest limit.
 */
fun percentUsed(status: UserStatus): Int? {
    if (!status.enabled) return null
    val allowed = status.allowedSeconds
    val remaining = status.remainingSeconds
    if (allowed <= 0.0 || remaining < 0.0) return null
    val used = (allowed - remaining).coerceIn(0.0, allowed)
    return floor(used / allowed * 100).toInt()
}

/** One limit of a user (daily, weekly, monthly or yearly, in lower case) and how much of it is used. */
data class PeriodPercent(val period: String, val percent: Int)

/**
 * The percent used of every limit the user has. With plugin 2.2 or newer that is each enabled limit, so a weekly limit at 90% is seen even while
 * the daily limit is at 50%. An older plugin reports only the limit closest to running out, so that one is used. Empty when the user has no limit
 * or is switched off.
 */
fun periodPercents(status: UserStatus): List<PeriodPercent> {
    if (!status.enabled) return emptyList()
    val reported = status.limits
    if (!reported.isNullOrEmpty()) {
        return reported.map { PeriodPercent(it.period.lowercase().ifEmpty { "daily" }, it.percentUsed.coerceIn(0, 100)) }
    }
    val closest = percentUsed(status) ?: return emptyList()
    return listOf(PeriodPercent(status.periodLabel, closest))
}

/** A percent rule that fired for one user, for one of their limits. */
data class PercentFire(val rule: NotifRule, val user: UserStatus, val period: String, val percent: Int)

/** [fires] to show now, and [state] to keep for the next update (replaces the old state). */
data class PercentResult(val fires: List<PercentFire>, val state: Map<String, String>)

/**
 * Decides which percent rules fire. A rule fires once per user and limit, when that limit reaches the percentage. It rearms when the percentage
 * drops below the rule, which happens at the limit's own reset, on a Reset from the app, or when time is added. [state] maps "ruleId|userId|period"
 * to the day it fired (kept for information only). A rule that was just added or switched on has no baseline yet: its first look only records where
 * things stand. On the first look after the app starts, anything already over the rule is recorded without firing. Pure Kotlin: unit tested.
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
            if (!rule.enabled || rule.event != NotifCatalog.Ids.HM_PERCENT) continue
            val percent = rule.int("percent", 80)
            val period = rule.text("period", "any")
            val hasBaseline = state.containsKey(seenKey(rule))
            next[seenKey(rule)] = state[seenKey(rule)] ?: today
            for (card in cards) {
                val user = card.status
                if (user.userId.isEmpty() || !rule.appliesTo(user.userId)) continue
                for (limit in periodPercents(user)) {
                    if (period != "any" && limit.period != period) continue
                    if (limit.percent < percent) continue
                    val key = key(rule, user, limit.period)
                    // Fired before and still over the rule: stay quiet. Hall Monitor's day starts at the user's reset time, not at midnight,
                    // and a weekly or monthly limit stays high for days, so the calendar date must not rearm it.
                    val alreadyFired = state.containsKey(key)
                    next[key] = state[key] ?: today
                    if (alreadyFired || firstLook || !hasBaseline) continue
                    fires.add(PercentFire(rule, user, limit.period, limit.percent))
                }
            }
        }
        return PercentResult(fires, next)
    }

    fun key(rule: NotifRule, user: UserStatus, period: String): String = "${rule.id}|${user.userId}|$period"

    /** Marks that a rule has been looked at while switched on (see the baseline note in [evaluate]). */
    fun seenKey(rule: NotifRule): String = "seen|${rule.id}"
}
