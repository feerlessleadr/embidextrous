package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The API version this app is built for (docs/api.md in the plugin repository). */
const val SUPPORTED_API_VERSION = 1

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class PluginInfo(
    @SerialName("Name") val name: String = "",
    @SerialName("PluginVersion") val pluginVersion: String = "",
    @SerialName("ApiVersion") val apiVersion: Int = 0,
)

@Serializable
data class ValueOption(
    @SerialName("Value") val value: String,
    @SerialName("Label") val label: String,
)

@Serializable
data class OperatorInfo(
    @SerialName("Value") val value: String,
    @SerialName("Label") val label: String,
)

@Serializable
data class FieldInfo(
    @SerialName("Name") val name: String,
    @SerialName("Label") val label: String,
    @SerialName("Category") val category: String = "",
    @SerialName("Type") val type: String = "Text",
    @SerialName("Operators") val operators: List<String> = emptyList(),
    @SerialName("UserSpecific") val userSpecific: Boolean = false,
    @SerialName("Values") val values: List<ValueOption>? = null,
)

@Serializable
data class SortInfo(
    @SerialName("Value") val value: String,
    @SerialName("Label") val label: String,
    @SerialName("Group") val group: String = "",
    @SerialName("Directionless") val directionless: Boolean = false,
    @SerialName("RoundRobin") val roundRobin: Boolean = false,
)

/** What the editor needs to build rules and sorts: served by the plugin at GET /catalog. */
@Serializable
data class Catalog(
    @SerialName("ApiVersion") val apiVersion: Int = 0,
    @SerialName("Fields") val fields: List<FieldInfo> = emptyList(),
    @SerialName("Operators") val operators: List<OperatorInfo> = emptyList(),
    @SerialName("Sorts") val sorts: List<SortInfo> = emptyList(),
    @SerialName("RoundRobinGroupByFields") val roundRobinGroupByFields: List<ValueOption> = emptyList(),
    @SerialName("WithinGroupOrders") val withinGroupOrders: List<ValueOption> = emptyList(),
    @SerialName("MediaTypes") val mediaTypes: List<String> = emptyList(),
    @SerialName("AutoRefreshModes") val autoRefreshModes: List<ValueOption> = emptyList(),
    @SerialName("RelativeDateUnits") val relativeDateUnits: List<ValueOption> = emptyList(),
    @SerialName("Weekdays") val weekdays: List<ValueOption> = emptyList(),
) {
    private val fieldsByName by lazy { fields.associateBy { it.name } }
    private val operatorLabels by lazy { operators.associate { it.value to it.label } }

    fun field(name: String): FieldInfo? = fieldsByName[name]
    fun fieldLabel(name: String): String = field(name)?.label ?: name
    fun operatorLabel(value: String): String = operatorLabels[value] ?: value
    fun sortLabel(value: String): String = sorts.firstOrNull { it.value == value }?.label ?: value
}

/** One user an Emby list can belong to (GET /users). */
data class EmbyUser(val id: String, val name: String)

/** A custom image of a list (Primary, Backdrop, ...). */
data class ImageInfo(val type: String, val fileName: String, val size: Long) {
    companion object {
        fun from(o: JsonObject) = ImageInfo(
            type = o.stringAt("ImageType").orEmpty(),
            fileName = o.stringAt("FileName").orEmpty(),
            size = (o["FileSize"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L,
        )
    }
}

/** The image types a list can have (the plugin's valid types), with plain labels. */
val IMAGE_TYPE_LABELS = listOf(
    "Primary" to "Poster / cover",
    "Backdrop" to "Backdrop",
    "Banner" to "Banner",
    "Thumb" to "Thumbnail",
    "Logo" to "Logo",
    "Disc" to "Disc",
    "Art" to "Clear art",
    "Box" to "Box front",
    "BoxRear" to "Box rear",
    "Menu" to "Menu",
)

/**
 * A smart list as the API returns it. The JSON is kept whole and edited in place, so properties this app does not
 * know about (or that a newer plugin adds) survive an update untouched.
 */
class SmartListDoc(val json: JsonObject) {
    val id: String get() = string("Id").orEmpty()
    val name: String get() = string("Name").orEmpty()
    val type: String get() = string("Type") ?: "Playlist"
    val isCollection: Boolean get() = type == "Collection"
    val enabled: Boolean get() = bool("Enabled") ?: true
    val itemCount: Int? get() = number("ItemCount")?.toInt()
    val totalRuntimeMinutes: Double? get() = number("TotalRuntimeMinutes")
    val lastRefreshed: String? get() = string("LastRefreshed")
    val autoRefresh: String get() = string("AutoRefresh") ?: "Never"
    val maxItems: Int? get() = number("MaxItems")?.toInt()?.takeIf { it > 0 }
    val maxPlayTimeMinutes: Int? get() = number("MaxPlayTimeMinutes")?.toInt()?.takeIf { it > 0 }
    val minItems: Int? get() = number("MinItems")?.toInt()?.takeIf { it > 0 }
    val mediaTypes: List<String> get() = strings("MediaTypes")
    val userId: String? get() = string("UserId")

    /** The Emby item of the list (the playlist of the first user, or the collection), for its cover image. */
    val embyItemId: String?
        get() = if (isCollection) {
            string("CollectionId")?.takeIf { it.isNotBlank() }
        } else {
            ((json["UserPlaylists"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.stringAt("PlaylistId") }.firstOrNull { it.isNotBlank() })
                ?: string("PlaylistId")?.takeIf { it.isNotBlank() }
        }

    /** The rule groups: groups are OR-ed, the rules inside a group are AND-ed. */
    val ruleGroups: List<List<JsonObject>>
        get() = (json["ExpressionSets"] as? JsonArray).orEmpty().map { set ->
            ((set as? JsonObject)?.get("Expressions") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        }

    val sortOptions: List<JsonObject>
        get() = (((json["Order"] as? JsonObject)?.get("SortOptions")) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    fun withValue(key: String, value: JsonElement?): SmartListDoc =
        SmartListDoc(JsonObject(json.toMutableMap().also { if (value == null || value is JsonNull) it.remove(key) else it[key] = value }))

    private fun string(key: String): String? = (json[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun bool(key: String): Boolean? = (json[key] as? JsonPrimitive)?.booleanOrNull
    private fun number(key: String): Double? = (json[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
    private fun strings(key: String): List<String> = (json[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    companion object {
        fun parse(text: String): SmartListDoc = SmartListDoc(AppJson.parseToJsonElement(text).jsonObject)
        fun parseArray(text: String): List<SmartListDoc> = AppJson.parseToJsonElement(text).jsonArray.map { SmartListDoc(it.jsonObject) }
    }
}

/** One rule, read out of the list JSON. */
fun JsonObject.ruleField(): String = (this["MemberName"] as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.ruleOperator(): String = (this["Operator"] as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.ruleValue(): String = (this["TargetValue"] as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.intOrNullAt(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
fun JsonObject.stringAt(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull


/** One item of a preview (POST /preview). */
data class PreviewItem(val id: Long, val name: String, val type: String, val detail: String?)

/** What a list's rules would pick right now: the full count and the first items. */
data class Preview(val total: Int, val items: List<PreviewItem>)

/** A refresh running right now for a list. */
data class RunningRefresh(val processed: Int, val total: Int)

/** How a list's last refresh went (GET /Status/History). */
data class RefreshOutcome(val success: Boolean, val error: String?, val endTime: String?)

/** Refreshes in progress and the last outcome per list, by list id. */
data class RefreshStatus(val running: Map<String, RunningRefresh>, val last: Map<String, RefreshOutcome>) {
    companion object { val Empty = RefreshStatus(emptyMap(), emptyMap()) }
}

/** A plugin backup file (GET /backups). */
data class BackupInfo(val filename: String, val createdAt: String?, val sizeBytes: Long, val listCount: Int)

/** What a restore did (POST /backups/{file}/restore). */
data class RestoreResult(val restored: Int, val overwritten: Int, val skipped: Int, val errors: Int)
