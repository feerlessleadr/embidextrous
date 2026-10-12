package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/** The server itself (GET /System/Info, administrators only). */
data class ServerInfo(
    val name: String,
    val version: String,
    val updateAvailable: Boolean,
    val restartPending: Boolean,
    val canSelfRestart: Boolean,
)

/** A plugin installed on the server (GET /Plugins). */
data class InstalledPlugin(val id: String, val name: String, val version: String)

/** A scheduled task whose last run failed (GET /ScheduledTasks). */
data class FailedTask(val name: String, val endedUtc: String?)

/** How many scheduled tasks the server has and which of them failed last time. */
data class TaskSummary(val total: Int, val failed: List<FailedTask>)

/** What is connected to the server right now (GET /Sessions). */
data class SessionCounts(val active: Int, val playing: Int, val transcoding: Int)

/** What the GitHub plugin manager says, for the Plugins card on Home: its gate, and its dashboard numbers when it is ready. */
data class GithubSummary(val gate: GhrpiGate, val status: GhStatusResponse?)

/** Everything the administrator cards on Home show. A part is null when its call failed, so one failure hides only its own line. */
data class ServerSnapshot(
    val info: ServerInfo?,
    val plugins: List<InstalledPlugin>?,
    val pluginUpdates: List<String>?,
    val tasks: TaskSummary?,
    val sessions: SessionCounts?,
    val github: GithubSummary? = null,
)

/** Turns the Emby answers into the small models above. Tolerant: unknown or missing fields give defaults, never a crash. No Android classes: unit tested. */
object ServerStatusParser {
    fun info(text: String): ServerInfo {
        val o = AppJson.parseToJsonElement(text).jsonObject
        return ServerInfo(
            name = o.stringAt("ServerName").orEmpty(),
            version = o.stringAt("Version").orEmpty(),
            updateAvailable = o.boolAt("HasUpdateAvailable"),
            restartPending = o.boolAt("HasPendingRestart"),
            canSelfRestart = o.boolAt("CanSelfRestart"),
        )
    }

    fun plugins(text: String): List<InstalledPlugin> =
        objects(text).mapNotNull { o ->
            val name = o.stringAt("Name") ?: return@mapNotNull null
            InstalledPlugin(o.stringAt("Id").orEmpty(), name, o.stringAt("Version").orEmpty())
        }.sortedBy { it.name.lowercase() }

    fun tasks(text: String): TaskSummary = TaskSummary(objects(text).size, failedTasks(text))

    /** Only runs that ended in "Failed" count: a cancelled or aborted run is not a fault. */
    fun failedTasks(text: String): List<FailedTask> =
        objects(text).mapNotNull { o ->
            val last = o["LastExecutionResult"] as? JsonObject ?: return@mapNotNull null
            if (last.stringAt("Status") != "Failed") return@mapNotNull null
            FailedTask(o.stringAt("Name") ?: last.stringAt("Name").orEmpty(), last.stringAt("EndTimeUtc"))
        }.sortedBy { it.name.lowercase() }

    /** Sessions that played or were active in the last [activeWithinMinutes] (Emby keeps idle ones for a while), how many play, how many transcode. */
    fun sessions(text: String, nowEpochMs: Long, activeWithinMinutes: Int = 10): SessionCounts {
        val limit = nowEpochMs - activeWithinMinutes * 60_000L
        var active = 0
        var playing = 0
        var transcoding = 0
        for (o in objects(text)) {
            val isPlaying = o["NowPlayingItem"] is JsonObject
            val seen = o.stringAt("LastActivityDate")?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            if (!isPlaying && (seen == null || seen < limit)) continue
            active++
            if (isPlaying) playing++
            if (isPlaying && o["TranscodingInfo"] is JsonObject) transcoding++
        }
        return SessionCounts(active, playing, transcoding)
    }

    /** Names of the plugins Emby's own catalog has updates for (GET /Packages/Updates). The shape of this answer is not verified; a name is used when there is one. */
    fun packageUpdates(text: String): List<String> =
        objects(text).map { it.stringAt("name") ?: it.stringAt("Name") ?: "A plugin" }

    private fun objects(text: String): List<JsonObject> =
        (AppJson.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonObject.boolAt(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
}

/** What the plugin versions mean for this app. */
object PluginChecks {
    /** Hall Monitor was called "Watching Eye" before 1.10; the app needs 1.9 or newer (regular accounts and now-playing). */
    val HALL_MONITOR_NAMES = setOf("Hall Monitor", "Watching Eye")
    const val HALL_MONITOR_MIN = "1.9"

    sealed interface Verdict {
        data object Missing : Verdict
        data class TooOld(val version: String) : Verdict
        data class Fine(val version: String) : Verdict
    }

    fun hallMonitor(plugins: List<InstalledPlugin>): Verdict {
        val p = plugins.firstOrNull { it.name in HALL_MONITOR_NAMES } ?: return Verdict.Missing
        return if (compareVersions(p.version, HALL_MONITOR_MIN) < 0) Verdict.TooOld(p.version) else Verdict.Fine(p.version)
    }

    fun smartLists(plugins: List<InstalledPlugin>): InstalledPlugin? = plugins.firstOrNull { it.name == "SmartLists" }

    /** Compares dotted numeric versions component by component; a missing component counts as 0 and a non-number as 0. */
    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
