package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.util.concurrent.atomic.AtomicLong

/** Stable ids for list items in the editor, so rows keep their state while others are added or removed. */
object DraftKeys {
    private val counter = AtomicLong()
    fun next(): Long = counter.incrementAndGet()
}

/**
 * One rule. [extras] holds every other property of the rule JSON (for example `IncludeUnwatchedSeries` or `UserId`),
 * so editing a rule never drops what this app does not show.
 */
data class RuleDraft(
    val key: Long = DraftKeys.next(),
    val field: String,
    val operator: String,
    val value: String,
    val extras: JsonObject = JsonObject(emptyMap()),
) {
    fun extraBool(name: String): Boolean? = (extras[name] as? JsonPrimitive)?.booleanOrNull
    fun extraString(name: String): String? = (extras[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    fun extraInt(name: String): Int? = (extras[name] as? JsonPrimitive)?.intOrNull

    fun withExtra(name: String, value: JsonElement?): RuleDraft =
        copy(extras = JsonObject(extras.toMutableMap().also { if (value == null) it.remove(name) else it[name] = value }))

    /** Sets or clears several extras at once (a null value clears that extra). */
    fun withExtras(changes: Map<String, JsonElement?>): RuleDraft =
        copy(extras = JsonObject(extras.toMutableMap().also { m -> changes.forEach { (k, v) -> if (v == null) m.remove(k) else m[k] = v } }))
}

/** A group of rules that must all match (AND). Groups are alternatives (OR). */
data class GroupDraft(
    val key: Long = DraftKeys.next(),
    val rules: List<RuleDraft>,
    val extras: JsonObject = JsonObject(emptyMap()),
)

data class SortDraft(
    val key: Long = DraftKeys.next(),
    val sortBy: String,
    val descending: Boolean = false,
    val groupBy: String? = null,
    val within: String? = null,
    val extras: JsonObject = JsonObject(emptyMap()),
)

/**
 * A schedule: when to refresh the list (refresh schedules) or when to enable or disable it (visibility schedules,
 * which carry an [action]). Only the fields that matter for the [trigger] are written.
 */
data class ScheduleDraft(
    val key: Long = DraftKeys.next(),
    val trigger: String = "Daily",
    /** Time of day, "HH:mm". */
    val time: String = "00:00",
    /** 0 = Sunday ... 6 = Saturday. */
    val dayOfWeek: Int = 0,
    val dayOfMonth: Int = 1,
    val month: Int = 1,
    /** A .NET time span such as "01:00:00" or "1.00:00:00". */
    val interval: String = "01:00:00",
    val action: String? = null,
    val extras: JsonObject = JsonObject(emptyMap()),
) {
    fun toJson(): JsonObject {
        val keep = extras.filterKeys { it !in SCHEDULE_KEYS }.toMutableMap()
        keep["Trigger"] = JsonPrimitive(trigger)
        val timeText = JsonPrimitive("$time:00")
        when (trigger) {
            "Daily" -> keep["Time"] = timeText
            "Weekly" -> {
                keep["DayOfWeek"] = JsonPrimitive(dayOfWeek)
                keep["Time"] = timeText
            }
            "Monthly" -> {
                keep["DayOfMonth"] = JsonPrimitive(dayOfMonth)
                keep["Time"] = timeText
            }
            "Yearly" -> {
                keep["Month"] = JsonPrimitive(month)
                keep["DayOfMonth"] = JsonPrimitive(dayOfMonth)
                keep["Time"] = timeText
            }
            "Interval" -> keep["Interval"] = JsonPrimitive(interval)
        }
        if (action != null) keep["Action"] = JsonPrimitive(action)
        return JsonObject(keep)
    }

    companion object {
        private val SCHEDULE_KEYS = setOf("Trigger", "Time", "DayOfWeek", "DayOfMonth", "Month", "Interval", "Action")

        fun from(json: JsonObject): ScheduleDraft = ScheduleDraft(
            trigger = json.stringAt("Trigger") ?: "Daily",
            time = json.stringAt("Time")?.take(5)?.takeIf { it.length == 5 } ?: "00:00",
            dayOfWeek = json.intOrNullAt("DayOfWeek") ?: dayNameToInt(json.stringAt("DayOfWeek")) ?: 0,
            dayOfMonth = json.intOrNullAt("DayOfMonth") ?: 1,
            month = json.intOrNullAt("Month") ?: 1,
            interval = json.stringAt("Interval") ?: "01:00:00",
            action = json.stringAt("Action"),
            extras = JsonObject(json.filterKeys { it !in SCHEDULE_KEYS }),
        )

        private fun dayNameToInt(name: String?): Int? =
            listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }
    }
}

/** "Pick one group at random" (a post-filter step before sorting and limits). */
data class RandomGroupDraft(
    val enabled: Boolean = false,
    val groupBy: String? = null,
    val minimumItems: String = "",
)

/** Items woven between a playlist's main items: their own rules, media types, order and spacing. */
data class BumperDraft(
    val groups: List<GroupDraft> = emptyList(),
    val mediaTypes: Set<String> = emptySet(),
    /** "Random", "Name" or "ReleaseDate". */
    val order: String = "Random",
    /** One bumper after every this many main items. */
    val interval: String = "1",
    val extras: JsonObject = JsonObject(emptyMap()),
)

/** Media types only a collection can hold (mirrors the admin page). */
val COLLECTION_ONLY_MEDIA_TYPES = setOf("Series", "Season", "MusicAlbum", "LiveTvChannel", "Collection", "Playlist")

/** Media types in the order the admin page lists them, with their labels. */
val MEDIA_TYPE_LABELS = listOf(
    "Movie" to "Movie",
    "Series" to "Series (TV show)",
    "Season" to "Season (TV show)",
    "Episode" to "Episode (TV show)",
    "Audio" to "Audio (music)",
    "MusicAlbum" to "Album (music)",
    "MusicVideo" to "Music video",
    "Video" to "Video",
    "Photo" to "Photo",
    "Book" to "Book",
    "AudioBook" to "Audiobook",
    "LiveTvChannel" to "Live TV channel",
    "Collection" to "Collection",
    "Playlist" to "Playlist",
)

private val VIDEO_CAPABLE = setOf("Movie", "Episode", "MusicVideo", "Video")
private val AUDIO_CAPABLE = setOf("Movie", "Episode", "Audio", "AudioBook", "MusicVideo", "Video")
private val CONTAINER_TYPES = setOf("Collection", "Playlist")
private val CONTAINER_METADATA_FIELDS = setOf(
    "Name", "Genres", "Studios", "ProductionYear", "OfficialRating", "DateCreated",
    "DateLastRefreshed", "DateLastSaved", "ReleaseDate", "Collections",
)

/** Rule fields that make sense for the chosen media types (the admin page hides the others too). */
fun fieldsFor(catalog: Catalog, mediaTypes: Set<String>): List<FieldInfo> {
    val onlyContainers = mediaTypes.isNotEmpty() && mediaTypes.all { it in CONTAINER_TYPES }
    return catalog.fields.filter { f ->
        when {
            onlyContainers -> f.name in CONTAINER_METADATA_FIELDS
            f.category == "Video" -> mediaTypes.isEmpty() || mediaTypes.any { it in VIDEO_CAPABLE }
            f.category == "Audio" -> mediaTypes.isEmpty() || mediaTypes.any { it in AUDIO_CAPABLE }
            else -> true
        }
    }
}

/** Fields a "pick one group at random" step can group by (the plugin's supported list). */
val RANDOM_GROUP_FIELDS = listOf(
    "SeriesName" to "Series name",
    "Genres" to "Genres",
    "Studios" to "Studios",
    "Tags" to "Tags",
    "Artists" to "Artists",
    "AlbumArtists" to "Album artists",
    "Album" to "Album",
)

/** A copy of [rule] with its own key, so both can sit in the same list. */
fun RuleDraft.duplicated(): RuleDraft = copy(key = DraftKeys.next())

/** The groups with [ruleKey] duplicated right after the original, inside the same group ([copy] when the caller made it). */
fun List<GroupDraft>.withRuleDuplicated(groupKey: Long, ruleKey: Long, copy: RuleDraft? = null): List<GroupDraft> = map { group ->
    if (group.key != groupKey) {
        group
    } else {
        group.copy(rules = group.rules.flatMap { r -> if (r.key == ruleKey) listOf(r, copy ?: r.duplicated()) else listOf(r) })
    }
}

/** The rule as the plugin's JSON (also used to describe it in words). */
fun RuleDraft.toJson(): JsonObject = JsonObject(
    extras.toMutableMap().also {
        it["MemberName"] = JsonPrimitive(field)
        it["Operator"] = JsonPrimitive(operator)
        it["TargetValue"] = JsonPrimitive(value)
    },
)

/** The groups with [groupKey] duplicated right after the original (an "or" alternative with the same rules). */
fun List<GroupDraft>.withGroupDuplicated(groupKey: Long): List<GroupDraft> = flatMap { group ->
    if (group.key == groupKey) listOf(group, group.copy(key = DraftKeys.next(), rules = group.rules.map { it.duplicated() })) else listOf(group)
}

/** A smart list being created or edited. */
data class ListDraft(
    /** The list JSON this draft came from (null for a new list): properties not shown here are kept on save. */
    val original: JsonObject? = null,
    val type: String = "Playlist",
    val name: String = "",
    val enabled: Boolean = true,
    /** Collections: the user whose watch state rules use. Playlists use [users] instead. */
    val ownerId: String? = null,
    /** Playlists: the users who get a playlist (ignored when [allUsers]). */
    val users: List<String> = emptyList(),
    /** Playlists: one playlist for every current and future user. */
    val allUsers: Boolean = false,
    /** Playlists for a single user: visible to everyone. */
    val isPublic: Boolean = false,
    val mediaTypes: Set<String> = emptySet(),
    val groups: List<GroupDraft> = emptyList(),
    val sorts: List<SortDraft> = listOf(SortDraft(sortBy = "Name")),
    val maxItems: String = "",
    val maxPlayTimeMinutes: String = "",
    val minItems: String = "",
    val autoRefresh: String = "Never",
    val schedules: List<ScheduleDraft> = emptyList(),
    val visibilitySchedules: List<ScheduleDraft> = emptyList(),
    // ----- presentation on the Emby list -----
    val sortTitle: String = "",
    val overview: String = "",
    /** Null: the plugin does not manage tags. An empty list clears the managed tags. */
    val tags: List<String>? = null,
    /** Null: the plugin does not manage the favorite state. */
    val favorite: Boolean? = null,
    // ----- advanced -----
    val includeExtras: Boolean = false,
    val matchByMembers: Boolean = false,
    val groupIntoCollections: Boolean = false,
    val randomGroup: RandomGroupDraft = RandomGroupDraft(),
    val similarityFields: List<String> = emptyList(),
    val bumpers: BumperDraft? = null,
) {
    val isNew: Boolean get() = original == null

    /**
     * This list as the start of a new one: same rules, sorting, schedules and options, a new name, and none of what
     * belongs to the existing list (its id, its Emby playlist ids, its images).
     */
    fun asCopy(): ListDraft = copy(
        original = null,
        name = if (name.endsWith(" (copy)")) name else "$name (copy)",
        groups = groups.map { g -> g.copy(key = DraftKeys.next(), rules = g.rules.map { it.duplicated() }) },
        sorts = sorts.map { it.copy(key = DraftKeys.next()) },
        schedules = schedules.map { it.copy(key = DraftKeys.next()) },
        visibilitySchedules = visibilitySchedules.map { it.copy(key = DraftKeys.next()) },
    )

    /**
     * This list as something to hand to someone else: like [asCopy] but with the name kept. The users are left out
     * (they are ids on this server); whoever imports it picks their own.
     */
    fun forSharing(): ListDraft = asCopy().copy(name = name, ownerId = null, users = emptyList(), allUsers = false)

    val isCollection: Boolean get() = type == "Collection"
    val hasSimilarRule: Boolean get() = groups.any { g -> g.rules.any { it.field == "SimilarTo" } }

    /** The first problem a person has to fix before saving, or null. The server validates again. */
    fun validate(catalog: Catalog?): String? {
        if (name.isBlank()) return "Give the list a name."
        if (mediaTypes.isEmpty()) return "Pick at least one media type."
        if (!isCollection) {
            val bad = mediaTypes.filter { it in COLLECTION_ONLY_MEDIA_TYPES }
            if (bad.isNotEmpty()) return "A playlist cannot hold: ${bad.joinToString(", ")}. Use a collection for those."
            if (!allUsers && users.isEmpty()) return "Pick at least one user the playlist is for."
        } else if (ownerId.isNullOrBlank()) {
            return "Pick the user whose watch state the rules use."
        }
        ruleProblem(groups, catalog)?.let { return it }
        for (sort in sorts) {
            val info = catalog?.sorts?.firstOrNull { it.value == sort.sortBy }
            if (info?.roundRobin == true && sort.groupBy.isNullOrBlank()) return "Pick what to group by for \"${info.label}\"."
        }
        for ((label, text) in listOf("Maximum items" to maxItems, "Maximum play time" to maxPlayTimeMinutes, "Minimum items" to minItems)) {
            if (text.isNotBlank() && (text.toIntOrNull() ?: -1) < 0) return "$label must be a whole number (or empty)."
        }
        if (randomGroup.enabled) {
            if (randomGroup.groupBy.isNullOrBlank()) return "Pick what to group by for the random group."
            if (randomGroup.minimumItems.isNotBlank() && (randomGroup.minimumItems.toIntOrNull() ?: -1) < 0) return "Minimum items in a group must be a whole number (or empty)."
        }
        bumpers?.let { b ->
            if (b.mediaTypes.isEmpty()) return "Bumpers: pick the media type of the bumpers."
            ruleProblem(b.groups, catalog)?.let { return "Bumpers: $it" }
            if ((b.interval.toIntOrNull() ?: 0) < 1) return "Bumpers: insert one after a whole number of items, 1 or more."
        }
        return null
    }

    private fun ruleProblem(groups: List<GroupDraft>, catalog: Catalog?): String? {
        for (group in groups) {
            for (rule in group.rules) {
                if (rule.value.isBlank()) {
                    val label = catalog?.fieldLabel(rule.field) ?: rule.field
                    return "Fill in the value for the \"$label\" rule, or remove it."
                }
            }
        }
        return null
    }

    /** The JSON to send: the original list with the edited properties applied. */
    fun toJson(): JsonObject {
        val result = (original?.toMutableMap() ?: mutableMapOf())
        result["Type"] = JsonPrimitive(type)
        result["Name"] = JsonPrimitive(name.trim())
        result["Enabled"] = JsonPrimitive(enabled)
        result["MediaTypes"] = mediaTypesJson(mediaTypes)
        result["ExpressionSets"] = setsJson(groups)
        val order = (original?.get("Order") as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        order["SortOptions"] = JsonArray(sorts.map(::sortJson))
        result["Order"] = JsonObject(order)
        result["MaxItems"] = maxItems.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        result["MaxPlayTimeMinutes"] = maxPlayTimeMinutes.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        result["MinItems"] = minItems.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        result["AutoRefresh"] = JsonPrimitive(autoRefresh)
        result["Schedules"] = JsonArray(schedules.map { it.toJson() })
        result["VisibilitySchedules"] = JsonArray(visibilitySchedules.map { it.toJson() })

        optionalString(result, "SortTitle", sortTitle)
        optionalString(result, "Overview", overview)
        if (tags != null) result["Tags"] = JsonArray(tags.map { JsonPrimitive(it) }) else result.remove("Tags")
        if (favorite != null) result["Favorite"] = JsonPrimitive(favorite) else result.remove("Favorite")

        result["IncludeExtras"] = JsonPrimitive(includeExtras)
        result["MatchByMembers"] = JsonPrimitive(matchByMembers)
        result["GroupIntoCollections"] = JsonPrimitive(isCollection && groupIntoCollections)
        if (randomGroup.enabled) {
            result["RandomGroupSelection"] = JsonObject(
                mapOf(
                    "Enabled" to JsonPrimitive(true),
                    "GroupBy" to JsonPrimitive(randomGroup.groupBy.orEmpty()),
                    "MinimumItems" to (randomGroup.minimumItems.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull),
                ),
            )
        } else {
            result.remove("RandomGroupSelection")
        }
        result["SimilarityComparisonFields"] = JsonArray(if (hasSimilarRule) similarityFields.map { JsonPrimitive(it) } else emptyList())

        if (isCollection) {
            result.remove("Bumpers")
            ownerId?.takeIf { it.isNotBlank() }?.let { result["UserId"] = JsonPrimitive(it) }
        } else {
            writePlaylistUsers(result)
            if (bumpers != null) result["Bumpers"] = bumperJson(bumpers) else result.remove("Bumpers")
        }
        return JsonObject(result)
    }

    private fun optionalString(target: MutableMap<String, JsonElement>, key: String, value: String) {
        if (value.isBlank()) target.remove(key) else target[key] = JsonPrimitive(value.trim())
    }

    /** Users of a playlist: keeps the Emby playlist id of users who stay, so their playlists are updated, not recreated. */
    private fun writePlaylistUsers(target: MutableMap<String, JsonElement>) {
        val existing = (original?.get("UserPlaylists") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        target["AllUsers"] = JsonPrimitive(allUsers)
        target["UserPlaylists"] = if (allUsers) {
            JsonArray(emptyList())
        } else {
            JsonArray(
                users.map { id ->
                    val previous = existing.firstOrNull { (it["UserId"] as? JsonPrimitive)?.content.equals(id, ignoreCase = true) }
                    JsonObject((previous?.toMutableMap() ?: mutableMapOf()).also { it["UserId"] = JsonPrimitive(id) })
                },
            )
        }
        target["Public"] = JsonPrimitive(!allUsers && users.size == 1 && isPublic)
        target.remove("UserId")
    }

    private fun mediaTypesJson(types: Set<String>) = JsonArray(
        types.sortedBy { t -> MEDIA_TYPE_LABELS.indexOfFirst { it.first == t }.let { if (it < 0) 99 else it } }.map { JsonPrimitive(it) },
    )

    private fun setsJson(groups: List<GroupDraft>) = JsonArray(
        groups.filter { it.rules.isNotEmpty() }.map { group ->
            JsonObject(group.extras.toMutableMap().also { it["Expressions"] = JsonArray(group.rules.map(::ruleJson)) })
        },
    )

    private fun bumperJson(b: BumperDraft): JsonObject = JsonObject(
        b.extras.toMutableMap().also {
            it["ExpressionSets"] = setsJson(b.groups)
            it["MediaTypes"] = mediaTypesJson(b.mediaTypes)
            it["BumperOrder"] = JsonPrimitive(b.order)
            it["Interval"] = JsonPrimitive(b.interval.toIntOrNull() ?: 1)
        },
    )

    private fun ruleJson(rule: RuleDraft): JsonObject = JsonObject(
        rule.extras.toMutableMap().also {
            it["MemberName"] = JsonPrimitive(rule.field)
            it["Operator"] = JsonPrimitive(rule.operator)
            it["TargetValue"] = JsonPrimitive(rule.value)
        },
    )

    private fun sortJson(sort: SortDraft): JsonObject = JsonObject(
        sort.extras.toMutableMap().also {
            it["SortBy"] = JsonPrimitive(sort.sortBy)
            it["SortOrder"] = JsonPrimitive(if (sort.descending) "Descending" else "Ascending")
            if (sort.groupBy != null) it["GroupByField"] = JsonPrimitive(sort.groupBy) else it.remove("GroupByField")
            if (sort.within != null) it["WithinGroupOrder"] = JsonPrimitive(sort.within) else it.remove("WithinGroupOrder")
        },
    )

    companion object {
        /** Reads an existing list into an editable draft. */
        fun from(doc: SmartListDoc): ListDraft {
            val json = doc.json
            val userPlaylists = (json["UserPlaylists"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val users = userPlaylists.mapNotNull { it.stringAt("UserId") }
            val allUsers = (json["AllUsers"] as? JsonPrimitive)?.booleanOrNull == true

            val sorts = doc.sortOptions.map { o ->
                SortDraft(
                    sortBy = o.stringAt("SortBy").orEmpty(),
                    descending = o.stringAt("SortOrder") == "Descending",
                    groupBy = o.stringAt("GroupByField"),
                    within = o.stringAt("WithinGroupOrder"),
                    extras = JsonObject(o.filterKeys { it !in setOf("SortBy", "SortOrder", "GroupByField", "WithinGroupOrder") }),
                )
            }
            val random = json["RandomGroupSelection"] as? JsonObject
            val bumper = json["Bumpers"] as? JsonObject

            return ListDraft(
                original = json,
                type = doc.type,
                name = doc.name,
                enabled = doc.enabled,
                ownerId = doc.userId,
                users = users.ifEmpty { listOfNotNull(doc.userId) },
                allUsers = allUsers,
                isPublic = (json["Public"] as? JsonPrimitive)?.booleanOrNull == true,
                mediaTypes = doc.mediaTypes.toSet(),
                groups = groupsFrom(json["ExpressionSets"]),
                sorts = sorts.ifEmpty { listOf(SortDraft(sortBy = "NoOrder")) },
                maxItems = doc.maxItems?.toString().orEmpty(),
                maxPlayTimeMinutes = doc.maxPlayTimeMinutes?.toString().orEmpty(),
                minItems = doc.minItems?.toString().orEmpty(),
                autoRefresh = doc.autoRefresh,
                schedules = (json["Schedules"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map(ScheduleDraft::from),
                visibilitySchedules = (json["VisibilitySchedules"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map(ScheduleDraft::from),
                sortTitle = json.stringAt("SortTitle").orEmpty(),
                overview = json.stringAt("Overview").orEmpty(),
                tags = (json["Tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content },
                favorite = (json["Favorite"] as? JsonPrimitive)?.booleanOrNull,
                includeExtras = (json["IncludeExtras"] as? JsonPrimitive)?.booleanOrNull == true,
                matchByMembers = (json["MatchByMembers"] as? JsonPrimitive)?.booleanOrNull == true,
                groupIntoCollections = (json["GroupIntoCollections"] as? JsonPrimitive)?.booleanOrNull == true,
                randomGroup = RandomGroupDraft(
                    enabled = (random?.get("Enabled") as? JsonPrimitive)?.booleanOrNull == true,
                    groupBy = random?.stringAt("GroupBy"),
                    minimumItems = random?.intOrNullAt("MinimumItems")?.toString().orEmpty(),
                ),
                similarityFields = (json["SimilarityComparisonFields"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
                bumpers = bumper?.let {
                    BumperDraft(
                        groups = groupsFrom(it["ExpressionSets"]),
                        mediaTypes = (it["MediaTypes"] as? JsonArray).orEmpty().mapNotNull { t -> (t as? JsonPrimitive)?.content }.toSet(),
                        order = it.stringAt("BumperOrder") ?: "Random",
                        interval = (it.intOrNullAt("Interval") ?: 1).toString(),
                        extras = JsonObject(it.filterKeys { k -> k !in setOf("ExpressionSets", "MediaTypes", "BumperOrder", "Interval") }),
                    )
                },
            )
        }

        /**
         * Reads a shared list (JSON pasted or opened from a file) as a new list owned by [ownerId]. Returns null when the
         * text is not the JSON of a smart list. Anything that identified the list on the other server is dropped by
         * [asCopy] and by [toJson] (the original is not kept).
         */
        fun fromShared(text: String, ownerId: String?): ListDraft? {
            // Refuse before parsing: the parser is recursive, so deeply nested text would overflow its stack.
            if (text.length > Safety.MAX_IMPORT_BYTES || Safety.jsonTooDeep(text)) return null
            val json = try {
                AppJson.parseToJsonElement(text) as? JsonObject
            } catch (e: Exception) {
                null
            } ?: return null
            if (json["Name"] == null || json["MediaTypes"] == null) return null
            val draft = from(SmartListDoc(json)).asCopy()
            return draft.copy(
                name = json.stringAt("Name").orEmpty(),
                ownerId = ownerId,
                users = listOfNotNull(ownerId),
                allUsers = false,
            )
        }

        private fun groupsFrom(sets: JsonElement?): List<GroupDraft> =
            (sets as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { set ->
                GroupDraft(
                    rules = (set["Expressions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map(::ruleFrom),
                    extras = JsonObject(set.filterKeys { it != "Expressions" }),
                )
            }

        private fun ruleFrom(json: JsonObject) = RuleDraft(
            field = json.ruleField(),
            operator = json.ruleOperator(),
            value = json.ruleValue(),
            extras = JsonObject(json.filterKeys { it !in setOf("MemberName", "Operator", "TargetValue") }),
        )
    }
}
