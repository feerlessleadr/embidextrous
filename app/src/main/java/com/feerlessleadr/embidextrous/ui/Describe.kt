package com.feerlessleadr.embidextrous.ui

import android.text.format.DateUtils
import com.feerlessleadr.embidextrous.data.Catalog
import com.feerlessleadr.embidextrous.data.SmartListDoc
import com.feerlessleadr.embidextrous.data.ruleField
import com.feerlessleadr.embidextrous.data.ruleOperator
import com.feerlessleadr.embidextrous.data.ruleValue
import com.feerlessleadr.embidextrous.data.stringAt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.Instant

/** Turns the plugin's JSON into short sentences for the screen. */
object Describe {

    fun rule(rule: JsonObject, catalog: Catalog?): String {
        val field = rule.ruleField()
        val label = catalog?.fieldLabel(field) ?: field
        val operator = rule.ruleOperator()
        val operatorText = catalog?.operatorLabel(operator) ?: operator
        val info = catalog?.field(field)

        var value = rule.ruleValue()
        value = when {
            info?.type == "Boolean" -> if (value.equals("true", ignoreCase = true)) "yes" else "no"
            operator == "NewerThan" || operator == "OlderThan" -> relativeDate(value)
            operator == "Weekday" -> catalog?.weekdays?.firstOrNull { it.value == value }?.label ?: value
            operator == "IsIn" || operator == "IsNotIn" -> value.split(';').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            info?.values != null -> info.values.firstOrNull { it.value == value }?.label?.substringBefore(" (") ?: value
            else -> value
        }

        val extras = buildList {
            if (field == "NextUnwatched" && (rule["IncludeUnwatchedSeries"] as? JsonPrimitive)?.booleanOrNull == false) {
                add("only shows you have started")
            }
        }
        val suffix = if (extras.isEmpty()) "" else " (${extras.joinToString(", ")})"
        return "$label $operatorText $value$suffix".trim()
    }

    /** "30:days" becomes "30 days". */
    private fun relativeDate(value: String): String {
        val parts = value.split(':')
        return if (parts.size == 2) "${parts[0]} ${parts[1]}" else value
    }

    /** One line per rule group; groups are alternatives ("or"), rules in a group all apply ("and"). */
    fun ruleGroups(doc: SmartListDoc, catalog: Catalog?): List<String> =
        doc.ruleGroups.filter { it.isNotEmpty() }.map { group -> group.joinToString("  and  ") { rule(it, catalog) } }

    fun sort(option: JsonObject, catalog: Catalog?): String {
        val by = option.stringAt("SortBy").orEmpty()
        val info = catalog?.sorts?.firstOrNull { it.value == by }
        val label = info?.label?.substringBefore(" (") ?: by
        val parts = mutableListOf(label)
        if (info?.directionless != true && option.stringAt("SortOrder") != null) {
            parts += if (option.stringAt("SortOrder") == "Descending") "descending" else "ascending"
        }
        option.stringAt("GroupByField")?.let { group ->
            parts += "grouped by " + (catalog?.roundRobinGroupByFields?.firstOrNull { it.value == group }?.label ?: group)
        }
        return parts.joinToString(", ")
    }

    fun autoRefresh(value: String, catalog: Catalog?): String =
        catalog?.autoRefreshModes?.firstOrNull { it.value == value }?.label ?: when (value) {
            "OnLibraryChanges" -> "When items are added to the library"
            "OnAllChanges" -> "On any change (including playback)"
            else -> "Manual only"
        }

    fun runtime(minutes: Double?): String? {
        if (minutes == null || minutes <= 0) return null
        val total = minutes.toLong()
        val days = total / (24 * 60)
        val hours = (total % (24 * 60)) / 60
        val mins = total % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    fun relativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "never"
        return try {
            DateUtils.getRelativeTimeSpanString(Instant.parse(iso).toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        } catch (e: Exception) {
            "never"
        }
    }

    /** "Oct 10, 1:44 AM": a date and time for things that should not read "in 0 minutes" when two clocks differ a little. */
    fun dateTime(context: android.content.Context, iso: String?): String {
        if (iso.isNullOrBlank()) return "Unknown time"
        return try {
            DateUtils.formatDateTime(
                context,
                Instant.parse(iso).toEpochMilli(),
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
            )
        } catch (e: Exception) {
            iso
        }
    }

    fun summary(doc: SmartListDoc): String {
        val parts = mutableListOf<String>()
        val count = doc.itemCount
        parts += if (count == null) "not refreshed yet" else if (count == 1) "1 item" else "$count items"
        runtime(doc.totalRuntimeMinutes)?.let { parts += it }
        if (doc.lastRefreshed != null) parts += "refreshed ${relativeTime(doc.lastRefreshed)}"
        return parts.joinToString(" · ")
    }
}
