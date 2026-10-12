package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * The "defaults for new lists" the server owner sets on the SmartLists plugin's own settings page (list type, media types, maximum items, minimum
 * items, play time, refresh mode, sorting, public or not, and a refresh schedule). The plugin's web page applies them to a new list; so does this app.
 * Everything is optional: a missing or unreadable value simply leaves the app's own starting value in place.
 */
data class ListDefaults(
    val type: String? = null,
    val mediaTypes: Set<String>? = null,
    val maxItems: Int? = null,
    val maxPlayTimeMinutes: Int? = null,
    val minItems: Int? = null,
    val autoRefresh: String? = null,
    val isPublic: Boolean? = null,
    val sortBy: String? = null,
    val sortDescending: Boolean = false,
    val schedule: ScheduleDraft? = null,
) {
    /**
     * The blank-list starting point with the defaults applied. [sortValues] are the sort fields the plugin offers (an unknown default sort is ignored).
     * Playlist or collection is chosen here too, as the plugin's page does.
     */
    fun applyToBlank(draft: ListDraft, sortValues: Set<String>?): ListDraft {
        var d = applyToTemplate(draft)
        type?.let { d = d.copy(type = it) }
        mediaTypes?.takeIf { it.isNotEmpty() }?.let { d = d.copy(mediaTypes = it) }
        autoRefresh?.let { d = d.copy(autoRefresh = it) }
        sortBy?.takeIf { sortValues == null || it in sortValues }?.let { s -> d = d.copy(sorts = listOf(SortDraft(sortBy = s, descending = sortDescending))) }
        return d
    }

    /**
     * For a template: only what a template does not decide for itself (the limits, the public switch and the refresh schedule). The template keeps
     * its own list type, media types, sorting and refresh mode, as on the plugin's page.
     */
    fun applyToTemplate(draft: ListDraft): ListDraft {
        var d = draft
        maxItems?.let { d = d.copy(maxItems = if (it > 0) it.toString() else "") }
        maxPlayTimeMinutes?.let { d = d.copy(maxPlayTimeMinutes = if (it > 0) it.toString() else "") }
        minItems?.let { d = d.copy(minItems = if (it > 0) it.toString() else "") }
        isPublic?.let { d = d.copy(isPublic = it) }
        schedule?.let { d = d.copy(schedules = listOf(it.copy(key = DraftKeys.next()))) }
        return d
    }

    companion object {
        /** Reads the plugin's configuration JSON. Anything unreadable gives an empty set of defaults. */
        fun parse(text: String): ListDefaults {
            val o = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return ListDefaults()
            fun str(key: String) = o.stringAt(key)?.takeIf { it.isNotBlank() }
            val trigger = str("DefaultScheduleTrigger")
            return ListDefaults(
                type = str("DefaultListType")?.takeIf { it == "Playlist" || it == "Collection" },
                mediaTypes = (o["DefaultMediaTypes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }?.toSet(),
                maxItems = o.intOrNullAt("DefaultMaxItems"),
                maxPlayTimeMinutes = o.intOrNullAt("DefaultMaxPlayTimeMinutes"),
                // The plugin's own page treats a missing value as 1, and so does it here.
                minItems = if ("DefaultMinItems" in o) o.intOrNullAt("DefaultMinItems") ?: 0 else 1,
                autoRefresh = str("DefaultAutoRefresh"),
                isPublic = (o["DefaultMakePublic"] as? JsonPrimitive)?.booleanOrNull,
                sortBy = str("DefaultSortBy"),
                sortDescending = str("DefaultSortOrder").equals("Descending", ignoreCase = true),
                schedule = if (trigger != null) schedule(o, trigger) else null,
            )
        }

        private fun schedule(o: JsonObject, trigger: String): ScheduleDraft? {
            if (trigger !in setOf("Daily", "Weekly", "Monthly", "Yearly", "Interval")) return null
            val seconds = isoSeconds(o.stringAt("DefaultScheduleTime")) ?: 0L
            val time = "%02d:%02d".format((seconds / 3600) % 24, (seconds % 3600) / 60)
            val day = o["DefaultScheduleDayOfWeek"]?.let { e ->
                val p = e as? JsonPrimitive
                p?.content?.toIntOrNull() ?: listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").indexOfFirst { it.equals(p?.content, true) }.takeIf { it >= 0 }
            } ?: 0
            return ScheduleDraft(
                trigger = trigger,
                time = time,
                dayOfWeek = day,
                dayOfMonth = o.intOrNullAt("DefaultScheduleDayOfMonth") ?: 1,
                month = o.intOrNullAt("DefaultScheduleMonth") ?: 1,
                interval = isoSeconds(o.stringAt("DefaultScheduleInterval"))?.takeIf { it > 0 }?.let(::timeSpan) ?: "00:15:00",
            )
        }

        /** Seconds in an ISO 8601 duration such as PT15M, PT1H30M or P1DT2H, or in a .NET time span such as 00:15:00 or 1.02:00:00. Null when it is neither. */
        fun isoSeconds(text: String?): Long? {
            val t = text?.trim().orEmpty()
            if (t.isEmpty()) return null
            if (t.startsWith("P", ignoreCase = true)) {
                val m = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$", RegexOption.IGNORE_CASE).matchEntire(t) ?: return null
                val (d, h, mi, s) = m.destructured
                return (d.toLongOrNull() ?: 0) * 86_400 + (h.toLongOrNull() ?: 0) * 3_600 + (mi.toLongOrNull() ?: 0) * 60 + (s.toDoubleOrNull()?.toLong() ?: 0)
            }
            val m = Regex("^(?:(\\d+)\\.)?(\\d{1,2}):(\\d{2}):(\\d{2})(?:\\.\\d+)?$").matchEntire(t) ?: return null
            val (d, h, mi, s) = m.destructured
            return (d.toLongOrNull() ?: 0) * 86_400 + h.toLong() * 3_600 + mi.toLong() * 60 + s.toLong()
        }

        /** A .NET time span the plugin accepts: "hh:mm:ss", or "d.hh:mm:ss" from a day up. */
        fun timeSpan(totalSeconds: Long): String {
            val days = totalSeconds / 86_400
            val rest = totalSeconds % 86_400
            val clock = "%02d:%02d:%02d".format(rest / 3600, (rest % 3600) / 60, rest % 60)
            return if (days > 0) "$days.$clock" else clock
        }
    }
}
