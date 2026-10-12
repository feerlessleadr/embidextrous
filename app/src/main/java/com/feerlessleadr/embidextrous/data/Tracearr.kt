package com.feerlessleadr.embidextrous.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

// Tracearr (https://github.com/connorgallopo/Tracearr) is a self-hosted playback monitor. This is its public API v2, read from its OpenAPI
// definition (apps/server/src/routes/publicV2.openapi.ts): bearer key "trr_pub_...", answers wrapped as {data, meta} with cursor paging
// (meta.nextCursor), one shared rate budget per key (240 a minute by default), images served from relative URLs without authentication.

/** One play (a resume chain) in Tracearr's history. */
data class TrPlay(
    val id: String,
    val serverName: String,
    val userId: String,
    val username: String,
    val mediaType: String,
    val title: String,
    val showTitle: String?,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    val artist: String?,
    val posterPath: String?,
    val watchedMs: Long,
    val totalMs: Long?,
    val percentComplete: Double?,
    val startedMs: Long?,
    val watched: Boolean,
    val device: String?,
    val platform: String?,
    val transcode: Boolean,
    val resolution: String?,
) {
    /** "Show · S2E5", the year, or the artist: the second line under a title. */
    val subtitle: String?
        get() = when {
            showTitle != null -> showTitle + if (season != null && episode != null) " · S${season}E$episode" else ""
            artist != null -> artist
            year != null -> year.toString()
            else -> null
        }
}

/** What is playing now on a Tracearr-watched server. */
data class TrStream(
    val id: String,
    val serverName: String,
    val username: String,
    val title: String,
    val subtitle: String?,
    val posterPath: String?,
    val paused: Boolean,
    val progressMs: Long,
    val durationMs: Long?,
    val device: String?,
    val platform: String?,
    val transcode: Boolean,
    val videoDecision: String?,
    val resolution: String?,
    val bitrateKbps: Int?,
)

data class TrStreamSummary(val total: Int, val transcodes: Int, val directStreams: Int, val directPlays: Int, val totalBitrate: String)

data class TrStreams(val streams: List<TrStream>, val summary: TrStreamSummary)

/** A violation: an automation of Tracearr's that fired (more than N streams, an unusual place, and so on). */
data class TrViolation(
    val id: String,
    val severity: String,
    val createdMs: Long?,
    val acknowledged: Boolean,
    val ruleName: String,
    val serverName: String,
    val userId: String,
    val username: String,
    /** The title that triggered it, when the automation recorded one. */
    val mediaTitle: String?,
    val ipAddress: String?,
)

data class TrServer(val id: String, val name: String, val type: String, val status: String, val activeStreams: Int, val version: String?, val historical: Boolean)

data class TrServers(val servers: List<TrServer>, val tracearrVersion: String)

data class TrAccount(val serverType: String, val username: String, val removed: Boolean)

/** A person as Tracearr knows them: one identity that can own accounts on several servers. */
data class TrUser(val id: String, val username: String, val accounts: List<TrAccount>)

data class TrWindow(val plays: Int, val watchTimeMs: Long)

data class TrUserStats(val userId: String, val allTime: TrWindow, val last30: TrWindow, val last7: TrWindow, val topGenres: List<Pair<String, Int>>)

/** A page of something, with the cursor for the next page (null when this was the last). */
data class TrPage<T>(val items: List<T>, val nextCursor: String?)

/** Parses Tracearr's answers. Tolerant: a missing field gives a default, an unknown field is ignored, garbage gives empty results. No Android classes: unit tested. */
object TracearrParser {
    private fun obj(text: String): JsonObject? = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject

    private fun data(text: String): List<JsonObject> = ((obj(text)?.get("data")) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun cursor(text: String): String? = (obj(text)?.get("meta") as? JsonObject)?.stringAt("nextCursor")

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun instant(text: String?): Long? = text?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    fun history(text: String): TrPage<TrPlay> = TrPage(
        data(text).mapNotNull { o ->
            val user = o["user"] as? JsonObject
            TrPlay(
                id = o.stringAt("id") ?: return@mapNotNull null,
                serverName = o.stringAt("server_name").orEmpty(),
                userId = user?.stringAt("id").orEmpty(),
                username = user?.stringAt("username").orEmpty(),
                mediaType = o.stringAt("media_type").orEmpty(),
                title = o.stringAt("media_title").orEmpty(),
                showTitle = o.stringAt("show_title"),
                season = o.intOrNullAt("season_number"),
                episode = o.intOrNullAt("episode_number"),
                year = o.intOrNullAt("year"),
                artist = o.stringAt("artist_name"),
                posterPath = o.stringAt("poster_url"),
                watchedMs = o.long("duration_ms") ?: 0L,
                totalMs = o.long("total_duration_ms"),
                percentComplete = (o["percent_complete"] as? JsonPrimitive)?.doubleOrNull,
                startedMs = instant(o.stringAt("started_at")),
                watched = o.bool("watched"),
                device = o.stringAt("device"),
                platform = o.stringAt("platform"),
                transcode = o.bool("is_transcode"),
                resolution = o.stringAt("resolution"),
            )
        },
        cursor(text),
    )

    fun streams(text: String): TrStreams {
        val root = obj(text)
        val s = root?.get("summary") as? JsonObject
        val list = data(text).mapNotNull { o ->
            val show = o.stringAt("show_title")
            val season = o.intOrNullAt("season_number")
            val episode = o.intOrNullAt("episode_number")
            TrStream(
                id = o.stringAt("id") ?: return@mapNotNull null,
                serverName = o.stringAt("server_name").orEmpty(),
                username = o.stringAt("username").orEmpty(),
                title = o.stringAt("media_title").orEmpty(),
                subtitle = when {
                    show != null -> show + if (season != null && episode != null) " · S${season}E$episode" else ""
                    o.stringAt("artist_name") != null -> o.stringAt("artist_name")
                    else -> o.intOrNullAt("year")?.toString()
                },
                posterPath = o.stringAt("poster_url"),
                paused = o.stringAt("state") == "paused",
                progressMs = o.long("progress_ms") ?: 0L,
                durationMs = o.long("duration_ms"),
                device = o.stringAt("device"),
                platform = o.stringAt("platform"),
                transcode = o.bool("is_transcode"),
                videoDecision = o.stringAt("video_decision"),
                resolution = o.stringAt("resolution"),
                bitrateKbps = o.intOrNullAt("bitrate"),
            )
        }
        return TrStreams(
            list,
            TrStreamSummary(
                total = s?.intOrNullAt("total") ?: list.size,
                transcodes = s?.intOrNullAt("transcodes") ?: 0,
                directStreams = s?.intOrNullAt("direct_streams") ?: 0,
                directPlays = s?.intOrNullAt("direct_plays") ?: 0,
                totalBitrate = s?.stringAt("total_bitrate").orEmpty(),
            ),
        )
    }

    fun violations(text: String): TrPage<TrViolation> = TrPage(
        data(text).mapNotNull { o ->
            val user = o["user"] as? JsonObject
            val rule = o["rule"] as? JsonObject
            val server = o["server"] as? JsonObject
            val details = o["data"] as? JsonObject
            TrViolation(
                id = o.stringAt("id") ?: return@mapNotNull null,
                severity = o.stringAt("severity") ?: "low",
                createdMs = instant(o.stringAt("created_at")),
                acknowledged = o.stringAt("acknowledged_at") != null,
                ruleName = rule?.stringAt("name") ?: details?.stringAt("ruleName").orEmpty(),
                serverName = server?.stringAt("name").orEmpty(),
                userId = user?.stringAt("id").orEmpty(),
                username = user?.stringAt("username").orEmpty(),
                mediaTitle = details?.stringAt("mediaTitle"),
                ipAddress = details?.stringAt("ipAddress"),
            )
        },
        cursor(text),
    )

    fun servers(text: String): TrServers = TrServers(
        data(text).mapNotNull { o ->
            TrServer(
                id = o.stringAt("server_id") ?: return@mapNotNull null,
                name = o.stringAt("server_name").orEmpty(),
                type = o.stringAt("server_type").orEmpty(),
                status = o.stringAt("status") ?: "unknown",
                activeStreams = o.intOrNullAt("active_streams") ?: 0,
                version = o.stringAt("version"),
                historical = o.bool("historical"),
            )
        },
        obj(text)?.stringAt("tracearr_version").orEmpty(),
    )

    fun users(text: String): TrPage<TrUser> = TrPage(
        data(text).mapNotNull { o ->
            TrUser(
                id = o.stringAt("id") ?: return@mapNotNull null,
                username = o.stringAt("username").orEmpty(),
                accounts = (o["accounts"] as? JsonArray).orEmpty().mapNotNull { a ->
                    val ao = a as? JsonObject ?: return@mapNotNull null
                    TrAccount(ao.stringAt("server_type").orEmpty(), ao.stringAt("username").orEmpty(), ao.stringAt("removed_at") != null)
                },
            )
        },
        cursor(text),
    )

    fun userStats(text: String): TrUserStats? {
        val o = obj(text) ?: return null
        val w = o["windows"] as? JsonObject
        fun window(key: String): TrWindow {
            val x = w?.get(key) as? JsonObject
            return TrWindow(x?.intOrNullAt("plays") ?: 0, x?.long("watch_time_ms") ?: 0L)
        }
        return TrUserStats(
            userId = o.stringAt("user_id").orEmpty(),
            allTime = window("all_time"),
            last30 = window("last_30"),
            last7 = window("last_7"),
            topGenres = (o["top_genres"] as? JsonArray).orEmpty().mapNotNull { g ->
                val go = g as? JsonObject ?: return@mapNotNull null
                (go.stringAt("genre") ?: return@mapNotNull null) to (go.intOrNullAt("plays") ?: 0)
            },
        )
    }

    /** The server's own sentence for a failed call, or null. */
    fun errorMessage(text: String): String? = obj(text)?.let { it.stringAt("message") ?: it.stringAt("error") }
}

/** Where a Tracearr connection stands. */
sealed interface TrGate {
    data object NotConfigured : TrGate
    data object Checking : TrGate
    data class Ready(val tracearrVersion: String, val servers: List<TrServer>) : TrGate
    data object KeyRejected : TrGate
    data class RateLimited(val retryAfterSeconds: Int?) : TrGate
    data class Unreachable(val message: String) : TrGate
}

/** Rules for the pieces of a Tracearr connection the person types in. */
object TracearrRules {
    /** A key looks like "trr_pub_" and then at least a few characters. Returns a reason when it clearly is not one. */
    fun checkKey(input: String): String? {
        val k = input.trim()
        return when {
            k.isEmpty() -> "Paste the API key from Tracearr (Settings, Data & API)."
            !k.startsWith("trr_pub_") -> "That does not look like a Tracearr API key. It starts with trr_pub_."
            k.length < 20 -> "That key is too short."
            k.length > 300 -> "That key is too long."
            k.any { it.isWhitespace() } -> "A key has no spaces in it."
            else -> null
        }
    }

    /** A poster or avatar path from Tracearr is only ever loaded when it is a path on the Tracearr server itself. */
    fun isOwnPath(path: String?): Boolean = path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("..") && path.length < 2000
}

/** Talks to the public API of the Tracearr server at [baseUrl] with the key. Reads only. */
class TracearrClient(baseUrl: String, private val key: String, private val http: OkHttpClient = ApiClient.defaultHttpClient()) {
    private val base = baseUrl.trimEnd('/')

    suspend fun servers(): TrServers = TracearrParser.servers(get("/api/v2/public/servers"))

    suspend fun streams(): TrStreams = TracearrParser.streams(get("/api/v2/public/streams"))

    suspend fun history(cursor: String? = null, pageSize: Int = 25, userId: String? = null): TrPage<TrPlay> =
        TracearrParser.history(get("/api/v2/public/history", "pageSize" to pageSize.coerceIn(1, 100), "cursor" to cursor, "user_id" to userId))

    suspend fun violations(cursor: String? = null, pageSize: Int = 25, pendingOnly: Boolean = false): TrPage<TrViolation> =
        TracearrParser.violations(get("/api/v2/public/violations", "pageSize" to pageSize.coerceIn(1, 100), "cursor" to cursor, "acknowledged" to if (pendingOnly) "false" else null))

    suspend fun users(cursor: String? = null): TrPage<TrUser> =
        TracearrParser.users(get("/api/v2/public/users", "pageSize" to 100, "cursor" to cursor))

    suspend fun userStats(id: String): TrUserStats? {
        if (!ID.matches(id)) throw ApiException(0, "Tracearr sent an unexpected identifier, so the request was not made.")
        return TracearrParser.userStats(get("/api/v2/public/users/$id/stats"))
    }

    /** A poster picture, only from a path on the Tracearr server (no authentication is needed or sent). */
    suspend fun image(path: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!TracearrRules.isOwnPath(path)) return@withContext null
        val url = (base + path).toHttpUrl()
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > Safety.MAX_COVER_BYTES) return@use null
                val source = body.source()
                source.request(Safety.MAX_COVER_BYTES + 1)
                if (source.buffer.size > Safety.MAX_COVER_BYTES) null else source.readByteArray()
            }
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun get(path: String, vararg query: Pair<String, Any?>): String = withContext(Dispatchers.IO) {
        val builder = (base + path).toHttpUrl().newBuilder()
        for ((k, v) in query) if (v != null) builder.addQueryParameter(k, v.toString())
        val request = Request.Builder().url(builder.build()).header("Authorization", "Bearer $key").header("Accept", "application/json").get().build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, describe(response.code, text, response.header("Retry-After")), if (response.code == 429) "RateLimited" else null)
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: javax.net.ssl.SSLException) {
            throw ApiException(0, "Could not make a secure connection to Tracearr: ${e.message ?: "certificate problem"}.")
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach Tracearr: ${e.message ?: "network error"}.")
        }
    }

    private fun describe(status: Int, body: String, retryAfter: String?): String = when {
        status == 401 -> "Tracearr did not accept the API key."
        status == 403 -> "That API key is not tied to an owner account in Tracearr."
        status == 429 -> "Tracearr is limiting requests" + (retryAfter?.toIntOrNull()?.let { " for another $it seconds" } ?: "") + "."
        status in 300..399 -> "The Tracearr address redirected the request (HTTP $status). The app does not follow redirects; check the address."
        status == 404 -> "Not found. Is that the address of Tracearr, and is it version 2.7 or newer?"
        else -> TracearrParser.errorMessage(body)?.takeIf { it.length < 200 } ?: "Tracearr answered with HTTP $status."
    }

    private companion object {
        val ID = Regex("[0-9a-fA-F-]{8,64}")
    }
}

/** A saved Tracearr connection: where it is and the key to it. */
data class TracearrConnection(val url: String, val key: String)
