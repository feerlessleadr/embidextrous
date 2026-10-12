package com.feerlessleadr.embidextrous.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** How a stream reaches the viewer: untouched, repackaged, or re-encoded (which costs the server work). */
enum class PlayMethod { Direct, DirectStream, Transcode, Unknown }

/** What a session is playing right now. */
data class PlayingNow(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val positionTicks: Long,
    val runTicks: Long,
    val paused: Boolean,
    val method: PlayMethod,
    val reasons: List<String>,
    val codecs: String?,
    /** The picture to show: an episode shows its show's poster, anything else its own. */
    val posterId: String = itemId,
)

/** Someone or something connected to the server (GET /Sessions). [userName] is empty for devices with no user, such as speakers and TVs found on the network. */
data class LiveSession(
    val id: String,
    val userId: String,
    val userName: String,
    val client: String,
    val device: String,
    val version: String,
    val remote: String,
    val lastActivityMs: Long?,
    val playing: PlayingNow?,
)

/** A scheduled task (GET /ScheduledTasks). */
data class ServerTask(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val running: Boolean,
    val progress: Double?,
    val lastStatus: String?,
    val lastEndedMs: Long?,
    val lastError: String?,
    val hidden: Boolean,
)

/** What kind of event an activity-log entry is, for its icon and filter. */
enum class ActivityKind { SignIn, SignInFailed, Playback, Plugin, User, Other }

/** One entry of the server's activity log (GET /System/ActivityLog/Entries). */
data class ActivityEntry(
    val id: Long,
    val title: String,
    val detail: String?,
    val type: String,
    val dateMs: Long?,
    val severity: String,
) {
    val kind: ActivityKind
        get() = when {
            type == "user.authenticated" -> ActivityKind.SignIn
            type == "user.authenticationfailed" -> ActivityKind.SignInFailed
            type.startsWith("playback") -> ActivityKind.Playback
            type.startsWith("plugin") || type.startsWith("package") || type.startsWith("installation") -> ActivityKind.Plugin
            type.startsWith("user.") -> ActivityKind.User
            else -> ActivityKind.Other
        }
    val isProblem: Boolean get() = severity.equals("Warn", true) || severity.equals("Warning", true) || severity.equals("Error", true) || severity.equals("Fatal", true) || kind == ActivityKind.SignInFailed
}

/** A device (an app on a phone, a browser) that has signed in to the server (GET /Devices). */
data class DeviceInfo(
    val id: String,
    val reportedId: String,
    val name: String,
    val appName: String,
    val appVersion: String,
    val userName: String,
    val lastActivityMs: Long?,
    val ip: String,
)

/** A media library (GET /Library/VirtualFolders). */
data class LibraryInfo(val id: String, val name: String, val type: String, val paths: List<String>)

/** Turns the Emby answers into the models above. Tolerant: missing fields give defaults, never a crash. No Android classes: unit tested. */
object ServerAdminParser {
    fun sessions(text: String): List<LiveSession> = objects(text).mapNotNull { o ->
        val id = o.stringAt("Id") ?: return@mapNotNull null
        val item = o["NowPlayingItem"] as? JsonObject
        val state = o["PlayState"] as? JsonObject
        val transcoding = o["TranscodingInfo"] as? JsonObject
        LiveSession(
            id = id,
            userId = o.stringAt("UserId").orEmpty(),
            userName = o.stringAt("UserName").orEmpty(),
            client = o.stringAt("Client").orEmpty(),
            device = o.stringAt("DeviceName").orEmpty(),
            version = o.stringAt("ApplicationVersion").orEmpty(),
            remote = o.stringAt("RemoteEndPoint").orEmpty(),
            lastActivityMs = instant(o.stringAt("LastActivityDate")),
            playing = item?.let { playing(it, state, transcoding) },
        )
    }

    private fun playing(item: JsonObject, state: JsonObject?, transcoding: JsonObject?): PlayingNow {
        val type = item.stringAt("Type").orEmpty()
        val series = item.stringAt("SeriesName")
        val season = item.intOrNullAt("ParentIndexNumber")
        val episode = item.intOrNullAt("IndexNumber")
        val year = item.intOrNullAt("ProductionYear")
        val subtitle = when {
            type == "Episode" && series != null -> series + if (season != null && episode != null) " · S${season}E$episode" else ""
            year != null -> year.toString()
            else -> item.stringAt("Album") ?: item.stringAt("AlbumArtist")
        }
        val method = when (state?.stringAt("PlayMethod")) {
            "DirectPlay" -> PlayMethod.Direct
            "DirectStream" -> PlayMethod.DirectStream
            "Transcode" -> PlayMethod.Transcode
            else -> if (transcoding != null) PlayMethod.Transcode else PlayMethod.Unknown
        }
        val reasons = (transcoding?.get("TranscodeReasons") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val codecs = listOfNotNull(transcoding?.stringAt("VideoCodec"), transcoding?.stringAt("AudioCodec")).joinToString(" + ").ifEmpty { null }
        return PlayingNow(
            itemId = item.stringAt("Id").orEmpty(),
            posterId = (if (type == "Episode") item.stringAt("SeriesId") else null) ?: item.stringAt("Id").orEmpty(),
            title = item.stringAt("Name").orEmpty(),
            subtitle = subtitle,
            positionTicks = (state?.get("PositionTicks") as? JsonPrimitive)?.longOrNull ?: 0L,
            runTicks = (item["RunTimeTicks"] as? JsonPrimitive)?.longOrNull ?: 0L,
            paused = (state?.get("IsPaused") as? JsonPrimitive)?.booleanOrNull ?: false,
            method = method,
            reasons = reasons,
            codecs = codecs,
        )
    }

    fun tasks(text: String): List<ServerTask> = objects(text).mapNotNull { o ->
        val id = o.stringAt("Id") ?: return@mapNotNull null
        val last = o["LastExecutionResult"] as? JsonObject
        ServerTask(
            id = id,
            name = o.stringAt("Name").orEmpty(),
            category = o.stringAt("Category").orEmpty(),
            description = o.stringAt("Description").orEmpty(),
            running = o.stringAt("State") == "Running",
            progress = (o["CurrentProgressPercentage"] as? JsonPrimitive)?.doubleOrNull,
            lastStatus = last?.stringAt("Status"),
            lastEndedMs = instant(last?.stringAt("EndTimeUtc")),
            lastError = last?.stringAt("ErrorMessage"),
            hidden = (o["IsHidden"] as? JsonPrimitive)?.booleanOrNull ?: false,
        )
    }

    fun activity(text: String): List<ActivityEntry> {
        val items = ((runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("Items") as? JsonArray).orEmpty()
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            ActivityEntry(
                id = (o["Id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null,
                title = o.stringAt("Name").orEmpty(),
                // The overview is several lines ("device and address", blank lines, then a "Time" label and the time): keep the first line before the time.
                detail = o.stringAt("ShortOverview") ?: o.stringAt("Overview")?.lineSequence()?.map { it.trim() }?.takeWhile { it != "Time" }?.firstOrNull { it.isNotEmpty() },
                type = o.stringAt("Type").orEmpty(),
                dateMs = instant(o.stringAt("Date")),
                severity = o.stringAt("Severity") ?: "Info",
            )
        }
    }

    fun devices(text: String): List<DeviceInfo> {
        val items = ((runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("Items") as? JsonArray).orEmpty()
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            DeviceInfo(
                id = o.stringAt("Id") ?: return@mapNotNull null,
                reportedId = o.stringAt("ReportedDeviceId").orEmpty(),
                name = o.stringAt("Name").orEmpty(),
                appName = o.stringAt("AppName").orEmpty(),
                appVersion = o.stringAt("AppVersion").orEmpty(),
                userName = o.stringAt("LastUserName").orEmpty(),
                lastActivityMs = instant(o.stringAt("DateLastActivity")),
                ip = o.stringAt("IpAddress").orEmpty(),
            )
        }.sortedByDescending { it.lastActivityMs ?: 0L }
    }

    fun libraries(text: String): List<LibraryInfo> = objects(text).mapNotNull { o ->
        LibraryInfo(
            id = o.stringAt("ItemId") ?: return@mapNotNull null,
            name = o.stringAt("Name").orEmpty(),
            type = o.stringAt("CollectionType").orEmpty(),
            paths = (o["Locations"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
        )
    }

    /** The item type to count in a library of [collectionType], and what to call them. */
    fun countFilter(collectionType: String): Pair<String?, String> = when (collectionType) {
        "movies" -> "Movie" to "movies"
        "tvshows" -> "Series" to "shows"
        "music" -> "MusicAlbum" to "albums"
        "boxsets" -> "BoxSet" to "collections"
        "playlists" -> "Playlist" to "playlists"
        else -> null to "items"
    }

    private fun objects(text: String): List<JsonObject> =
        ((runCatching { AppJson.parseToJsonElement(text) }.getOrNull()) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun instant(text: String?): Long? = text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}
