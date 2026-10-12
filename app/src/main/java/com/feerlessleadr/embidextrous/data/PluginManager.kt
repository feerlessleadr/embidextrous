package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * The GitHub Repo Plugin Install plugin's phone API (see its docs/EMBIDEXTROUS-INTEGRATION.md). The app was built for API version
 * [SUPPORTED]; a plugin below it is too old, a plugin whose oldest supported version is above it needs a newer app.
 */
object Ghrpi {
    const val SUPPORTED = 1
}

/** GET /info: the plugin's versions and the two switches the server owner controls on the plugin's own page. */
data class GhrpiInfo(
    val pluginVersion: String,
    val apiVersion: Int,
    val minSupportedApiVersion: Int,
    val apiEnabled: Boolean,
    val allowManage: Boolean,
    val runInProgress: Boolean,
    val restartPending: Boolean,
)

/** Where the plugin manager stands, worked out from /info (or from there being no plugin). */
sealed interface GhrpiGate {
    data object Checking : GhrpiGate

    /** The plugin is not installed on this server (the request found nothing). */
    data object NotInstalled : GhrpiGate

    /** The plugin is there but its phone API is switched off in its own settings. */
    data class Off(val info: GhrpiInfo?) : GhrpiGate

    data class PluginTooOld(val info: GhrpiInfo) : GhrpiGate
    data class AppTooOld(val info: GhrpiInfo) : GhrpiGate
    data class Ready(val info: GhrpiInfo) : GhrpiGate
    data class Unreachable(val message: String) : GhrpiGate

    companion object {
        fun of(info: GhrpiInfo): GhrpiGate = when {
            info.apiVersion < Ghrpi.SUPPORTED -> PluginTooOld(info)
            info.minSupportedApiVersion > Ghrpi.SUPPORTED -> AppTooOld(info)
            !info.apiEnabled -> Off(info)
            else -> Ready(info)
        }
    }
}

/** A plugin's state as the plugin reports it. An unknown word becomes [Unknown], so a newer plugin cannot break the app. */
enum class GhStatus { NotChecked, NoRelease, UpToDate, UpdateAvailable, UpdateWaiting, Failed, Unknown;

    val hasUpdate: Boolean get() = this == UpdateAvailable || this == UpdateWaiting

    companion object {
        fun from(text: String?): GhStatus = entries.firstOrNull { it.name == text && it != Unknown } ?: Unknown
    }
}

data class GhBackup(val version: String?, val createdMs: Long?, val sizeBytes: Long)

/** One plugin the installer looks after. */
data class GhPlugin(
    val id: String,
    val name: String,
    val owner: String,
    val url: String,
    val status: GhStatus,
    val statusText: String,
    val autoUpdate: Boolean,
    val allowPreRelease: Boolean,
    val assetNameFilter: String,
    val installedVersion: String?,
    val latestVersion: String?,
    val latestPublishedMs: Long?,
    val lastCheckedMs: Long?,
    val lastUpdatedMs: Long?,
    val lastError: String?,
    val releaseNotes: String?,
    val assetNames: List<String>,
    val restartPending: Boolean,
    /** Only on the single-plugin call; null in the list. */
    val backups: List<GhBackup>?,
)

data class GhRunProgress(val isRunning: Boolean, val total: Int, val done: Int, val current: String?, val trigger: String?)

data class GhRunResult(val pluginId: String, val name: String, val outcome: String, val fromVersion: String?, val toVersion: String?, val message: String?)

data class GhRun(
    val id: String,
    val startedMs: Long?,
    val durationSeconds: Double,
    val trigger: String,
    val updated: Int,
    val upToDate: Int,
    val failed: Int,
    val waiting: Int,
    val results: List<GhRunResult>,
)

data class GhPendingRestart(val name: String, val fromVersion: String?, val toVersion: String?)

data class GhCounts(val total: Int, val upToDate: Int, val updateAvailable: Int, val updateWaiting: Int, val failed: Int, val notChecked: Int, val noRelease: Int)

/** GET /status: the dashboard in one answer. */
data class GhStatusResponse(
    val run: GhRunProgress,
    val lastRun: GhRun?,
    val restartPending: Boolean,
    val pendingRestart: List<GhPendingRestart>,
    val canSelfRestart: Boolean,
    val counts: GhCounts,
    val updatesReady: Int,
)

data class GhAvailable(val name: String, val description: String?, val url: String, val registry: String?, val alreadyAdded: Boolean)

data class GhRegistry(val id: String, val name: String, val rawUrl: String, val enabled: Boolean, val builtIn: Boolean)

data class GhSettings(val tokenSet: Boolean, val restartServerAfterInstall: Boolean, val apiEnabled: Boolean, val allowManage: Boolean)

/** What an action answered: a sentence for the person, whether a restart is now needed, and the plugin as it is now (when the call returns one). */
data class GhActionResult(val message: String, val restartNeeded: Boolean, val plugin: GhPlugin?)

/** The mode of a run: Auto (what the daily task does), All, Check (look only) or Available (install only what has an update). */
enum class GhRunMode { Auto, All, Check, Available }

/** An error the plugin itself produced: {Title, Status, Detail}. Emby's own 401 and 403 are plain text and are not this. */
data class GhError(val title: String, val status: Int, val detail: String)

/** Turns the plugin's JSON into the models above. Tolerant: missing fields give defaults; unknown fields and words are ignored. No Android classes: unit tested. */
object GhrpiParser {
    fun info(text: String): GhrpiInfo {
        val o = obj(text) ?: JsonObject(emptyMap())
        return GhrpiInfo(
            pluginVersion = o.stringAt("PluginVersion").orEmpty(),
            apiVersion = o.intOrNullAt("ApiVersion") ?: 0,
            minSupportedApiVersion = o.intOrNullAt("MinSupportedApiVersion") ?: 0,
            apiEnabled = o.bool("ApiEnabled"),
            allowManage = o.bool("AllowManage"),
            runInProgress = o.bool("RunInProgress"),
            restartPending = o.bool("RestartPending"),
        )
    }

    fun plugin(o: JsonObject): GhPlugin? {
        val id = o.stringAt("Id") ?: return null
        return GhPlugin(
            id = id,
            name = o.stringAt("Name").orEmpty(),
            owner = o.stringAt("Owner").orEmpty(),
            url = o.stringAt("Url").orEmpty(),
            status = GhStatus.from(o.stringAt("Status")),
            statusText = o.stringAt("StatusText").orEmpty(),
            autoUpdate = o.bool("AutoUpdate"),
            allowPreRelease = o.bool("AllowPreRelease"),
            assetNameFilter = o.stringAt("AssetNameFilter").orEmpty(),
            installedVersion = o.stringAt("InstalledVersion"),
            latestVersion = o.stringAt("LatestVersion"),
            latestPublishedMs = instant(o.stringAt("LatestPublishedUtc")),
            lastCheckedMs = instant(o.stringAt("LastCheckedUtc")),
            lastUpdatedMs = instant(o.stringAt("LastUpdatedUtc")),
            lastError = o.stringAt("LastError"),
            releaseNotes = o.stringAt("ReleaseNotes"),
            assetNames = strings(o["AssetNames"]),
            restartPending = o.bool("RestartPending"),
            backups = (o["Backups"] as? JsonArray)?.mapNotNull { b ->
                val bo = b as? JsonObject ?: return@mapNotNull null
                GhBackup(bo.stringAt("Version"), instant(bo.stringAt("CreatedUtc")), (bo["SizeBytes"] as? JsonPrimitive)?.longOrNull ?: 0L)
            },
        )
    }

    fun pluginList(text: String): List<GhPlugin> = items(text).mapNotNull(::plugin)

    fun singlePlugin(text: String): GhPlugin? = obj(text)?.let(::plugin)

    fun status(text: String): GhStatusResponse {
        val o = obj(text) ?: JsonObject(emptyMap())
        val run = o["Run"] as? JsonObject
        val counts = o["Counts"] as? JsonObject
        return GhStatusResponse(
            run = GhRunProgress(run?.bool("IsRunning") ?: false, run?.intOrNullAt("Total") ?: 0, run?.intOrNullAt("Done") ?: 0, run?.stringAt("Current"), run?.stringAt("Trigger")),
            lastRun = (o["LastRun"] as? JsonObject)?.let(::run),
            restartPending = o.bool("RestartPending"),
            pendingRestart = (o["PendingRestart"] as? JsonArray).orEmpty().mapNotNull { e ->
                val p = e as? JsonObject ?: return@mapNotNull null
                GhPendingRestart(p.stringAt("Name").orEmpty(), p.stringAt("FromVersion"), p.stringAt("ToVersion"))
            },
            canSelfRestart = o.bool("CanSelfRestart"),
            counts = GhCounts(
                total = counts?.intOrNullAt("Total") ?: 0,
                upToDate = counts?.intOrNullAt("UpToDate") ?: 0,
                updateAvailable = counts?.intOrNullAt("UpdateAvailable") ?: 0,
                updateWaiting = counts?.intOrNullAt("UpdateWaiting") ?: 0,
                failed = counts?.intOrNullAt("Failed") ?: 0,
                notChecked = counts?.intOrNullAt("NotChecked") ?: 0,
                noRelease = counts?.intOrNullAt("NoRelease") ?: 0,
            ),
            updatesReady = o.intOrNullAt("UpdatesReady") ?: 0,
        )
    }

    private fun run(o: JsonObject) = GhRun(
        id = o.stringAt("Id").orEmpty(),
        startedMs = instant(o.stringAt("StartedUtc")),
        durationSeconds = (o["DurationSeconds"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        trigger = o.stringAt("Trigger").orEmpty(),
        updated = o.intOrNullAt("Updated") ?: 0,
        upToDate = o.intOrNullAt("UpToDate") ?: 0,
        failed = o.intOrNullAt("Failed") ?: 0,
        waiting = o.intOrNullAt("Waiting") ?: 0,
        results = (o["Results"] as? JsonArray).orEmpty().mapNotNull { e ->
            val r = e as? JsonObject ?: return@mapNotNull null
            GhRunResult(r.stringAt("PluginId").orEmpty(), r.stringAt("Name").orEmpty(), r.stringAt("Outcome").orEmpty(), r.stringAt("FromVersion"), r.stringAt("ToVersion"), r.stringAt("Message"))
        },
    )

    fun history(text: String): List<GhRun> = items(text).map(::run)

    fun available(text: String): List<GhAvailable> = items(text).mapNotNull { o ->
        GhAvailable(
            name = o.stringAt("Name") ?: return@mapNotNull null,
            description = o.stringAt("Description"),
            url = o.stringAt("Url") ?: return@mapNotNull null,
            registry = o.stringAt("RegistrySource"),
            alreadyAdded = o.bool("AlreadyAdded"),
        )
    }

    fun registries(text: String): List<GhRegistry> = items(text).mapNotNull { o ->
        GhRegistry(o.stringAt("Id") ?: return@mapNotNull null, o.stringAt("Name").orEmpty(), o.stringAt("RawUrl").orEmpty(), o.bool("Enabled", default = true), o.bool("BuiltIn"))
    }

    fun settings(text: String): GhSettings {
        val o = obj(text) ?: JsonObject(emptyMap())
        return GhSettings(o.bool("TokenSet"), o.bool("RestartServerAfterInstall"), o.bool("ApiEnabled"), o.bool("AllowManage"))
    }

    fun action(text: String): GhActionResult {
        val o = obj(text) ?: JsonObject(emptyMap())
        return GhActionResult(o.stringAt("Message").orEmpty(), o.bool("RestartNeeded"), (o["Plugin"] as? JsonObject)?.let(::plugin))
    }

    /** The test-token answer: ok or not, and the sentence to show. */
    fun tokenTest(text: String): Pair<Boolean, String> {
        val o = obj(text) ?: JsonObject(emptyMap())
        return o.bool("Ok") to o.stringAt("Message").orEmpty()
    }

    /** The plugin's own error body, or null when the body is something else (Emby's plain text, HTML, nothing). */
    fun error(text: String): GhError? {
        val o = obj(text) ?: return null
        val title = o.stringAt("Title") ?: return null
        return GhError(title, o.intOrNullAt("Status") ?: 0, o.stringAt("Detail").orEmpty())
    }

    /** A GitHub address as the plugin wants it: "owner/repository" or an https GitHub link, at most 300 characters. Returns a reason when it is not usable, or null when it is fine. */
    fun checkRepoAddress(input: String): String? {
        val t = input.trim()
        return when {
            t.isEmpty() -> "Enter owner/repository or a GitHub link."
            t.length > 300 -> "That address is too long."
            t.startsWith("http", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true) -> "Use an https:// address."
            !t.contains('/') -> "Enter it as owner/repository, for example feerlessleadr/hall-monitor-emby."
            else -> null
        }
    }

    private fun obj(text: String): JsonObject? = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject

    private fun items(text: String): List<JsonObject> {
        val root = runCatching { AppJson.parseToJsonElement(text) }.getOrNull()
        val array = (root as? JsonObject)?.get("Items") as? JsonArray ?: root as? JsonArray
        return array.orEmpty().mapNotNull { it as? JsonObject }
    }

    private fun strings(e: kotlinx.serialization.json.JsonElement?): List<String> = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    private fun JsonObject.bool(key: String, default: Boolean = false): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

    private fun instant(text: String?): Long? = text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

/** How the plugins are ordered on the screen: problems first, then updates, then the rest by name. */
object GhOrder {
    fun sorted(plugins: List<GhPlugin>): List<GhPlugin> =
        plugins.sortedWith(compareBy({ rank(it.status) }, { it.name.lowercase() }))

    private fun rank(s: GhStatus) = when (s) {
        GhStatus.Failed -> 0
        GhStatus.UpdateAvailable, GhStatus.UpdateWaiting -> 1
        GhStatus.NotChecked, GhStatus.Unknown -> 2
        GhStatus.UpToDate -> 3
        GhStatus.NoRelease -> 4
    }
}
