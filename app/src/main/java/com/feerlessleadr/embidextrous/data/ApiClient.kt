package com.feerlessleadr.embidextrous.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A failed call. [status] is the HTTP status, or 0 when the server could not be reached. */
class ApiException(val status: Int, message: String, val code: String? = null) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
}

/** Result of signing in. */
data class SignInResult(val token: String, val userId: String, val userName: String, val isAdministrator: Boolean)

/**
 * Talks to Emby and the SmartLists plugin (see docs/api.md in the plugin repository). Every call carries the
 * access token in the X-Emby-Token header, never in the URL.
 */
class ApiClient(
    serverUrl: String,
    private val token: String? = null,
    private val appName: String = "Embidextrous",
    private val appVersion: String = "0",
    private val deviceId: String = "unknown",
    private val deviceName: String = "Android",
    private val http: OkHttpClient = defaultHttpClient(),
) {
    private val server = serverUrl.trimEnd('/')
    private val pluginBase = "$server/emby/Plugins/SmartLists"

    // ----- sign in -------------------------------------------------------------------------------------------

    suspend fun signIn(userName: String, password: String): SignInResult {
        val body = buildJsonObject {
            put("Username", userName)
            put("Pw", password)
        }
        val text = execute(
            Request.Builder()
                .url("$server/emby/Users/AuthenticateByName".toHttpUrl())
                .header("X-Emby-Authorization", authorizationHeader())
                .post(body.toString().toRequestBody(JSON)),
            authenticated = false,
        )
        val root = AppJson.parseToJsonElement(text).jsonObject
        val user = root["User"]?.jsonObject
        val policy = user?.get("Policy")?.jsonObject
        return SignInResult(
            token = (root["AccessToken"] as? JsonPrimitive)?.contentOrNull ?: throw ApiException(0, "The server did not return an access token."),
            userId = (user?.get("Id") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            userName = (user?.get("Name") as? JsonPrimitive)?.contentOrNull ?: userName,
            isAdministrator = (policy?.get("IsAdministrator") as? JsonPrimitive)?.contentOrNull == "true",
        )
    }

    // ----- plugin --------------------------------------------------------------------------------------------

    suspend fun info(): PluginInfo = AppJson.decodeFromString(PluginInfo.serializer(), get("$pluginBase/info"))

    suspend fun catalog(): Catalog = AppJson.decodeFromString(Catalog.serializer(), get("$pluginBase/catalog"))

    suspend fun lists(): List<SmartListDoc> = SmartListDoc.parseArray(get("$pluginBase/"))

    suspend fun list(id: String): SmartListDoc = SmartListDoc.parse(get("$pluginBase/${seg(id)}"))

    suspend fun create(doc: JsonObject, skipRefresh: Boolean = false): SmartListDoc =
        SmartListDoc.parse(send("POST", "$pluginBase/?skipRefresh=$skipRefresh", doc.toString()))

    suspend fun update(id: String, doc: JsonObject, skipRefresh: Boolean = false): SmartListDoc =
        SmartListDoc.parse(send("PUT", "$pluginBase/${seg(id)}?skipRefresh=$skipRefresh", doc.toString()))

    suspend fun delete(id: String, deleteEmbyList: Boolean) {
        send("DELETE", "$pluginBase/${seg(id)}?deleteEmbyList=$deleteEmbyList", null)
    }

    suspend fun enable(id: String) {
        send("POST", "$pluginBase/${seg(id)}/enable", "")
    }

    suspend fun disable(id: String) {
        send("POST", "$pluginBase/${seg(id)}/disable", "")
    }

    suspend fun refresh(id: String) {
        send("POST", "$pluginBase/${seg(id)}/refresh", "")
    }

    /** The "defaults for new lists" from the SmartLists plugin's settings (an administrator may read the plugin's configuration). */
    suspend fun listDefaults(): ListDefaults = ListDefaults.parse(get("$server/emby/Plugins/$SMARTLISTS_PLUGIN_ID/Configuration"))

    suspend fun refreshAll() {
        send("POST", "$pluginBase/refresh", "")
    }

    // ----- server state (Emby's own endpoints, administrators only) ------------------------------------------

    suspend fun systemInfo(): ServerInfo = ServerStatusParser.info(get("$server/emby/System/Info"))

    suspend fun installedPlugins(): List<InstalledPlugin> = ServerStatusParser.plugins(get("$server/emby/Plugins"))

    suspend fun scheduledTasks(): TaskSummary = ServerStatusParser.tasks(get("$server/emby/ScheduledTasks"))

    suspend fun sessionCounts(): SessionCounts = ServerStatusParser.sessions(get("$server/emby/Sessions"), System.currentTimeMillis())

    suspend fun pluginUpdates(): List<String> = ServerStatusParser.packageUpdates(get("$server/emby/Packages/Updates"))

    // ----- server tools (administrators only): sessions, tasks, activity, devices, libraries -----------------

    suspend fun liveSessions(): List<LiveSession> = ServerAdminParser.sessions(get("$server/emby/Sessions"))

    /** Asks the session's app to stop playing. The server answers 204 and the app obeys, or does not. */
    suspend fun stopPlayback(sessionId: String) {
        send("POST", "$server/emby/Sessions/${seg(sessionId)}/Playing/Stop", "")
    }

    suspend fun allTasks(): List<ServerTask> = ServerAdminParser.tasks(get("$server/emby/ScheduledTasks"))

    suspend fun runTask(id: String) {
        send("POST", "$server/emby/ScheduledTasks/Running/${seg(id)}", "")
    }

    suspend fun stopTask(id: String) {
        send("DELETE", "$server/emby/ScheduledTasks/Running/${seg(id)}", null)
    }

    suspend fun activity(start: Int, limit: Int): List<ActivityEntry> =
        ServerAdminParser.activity(get("$server/emby/System/ActivityLog/Entries?StartIndex=$start&Limit=$limit"))

    suspend fun devices(): List<DeviceInfo> = ServerAdminParser.devices(get("$server/emby/Devices"))

    /** The name an administrator gave a device (shown instead of the one it reports), or null when none was set. */
    suspend fun deviceCustomName(id: String): String? =
        (AppJson.parseToJsonElement(get("$server/emby/Devices/Options?Id=${seg(id)}")) as? JsonObject)?.stringAt("CustomName")?.takeIf { it.isNotBlank() }

    /** Gives a device a name of your own; an empty [name] goes back to the name the device reports. */
    suspend fun setDeviceCustomName(id: String, name: String) {
        send("POST", "$server/emby/Devices/Options?Id=${seg(id)}", buildJsonObject { put("CustomName", name.trim()) }.toString())
    }

    /** Signs a device out for good: its token stops working and it disappears from the list. */
    suspend fun revokeDevice(id: String) {
        send("DELETE", "$server/emby/Devices?Id=${seg(id)}", null)
    }

    suspend fun mediaLibraries(): List<LibraryInfo> = ServerAdminParser.libraries(get("$server/emby/Library/VirtualFolders"))

    /** How many items of the library's main kind it holds (null when the server would not say). */
    suspend fun libraryCount(id: String, collectionType: String): Int? {
        val kind = ServerAdminParser.countFilter(collectionType).first
        val filter = if (kind != null) "&IncludeItemTypes=$kind" else ""
        val root = AppJson.parseToJsonElement(get("$server/emby/Items?ParentId=${seg(id)}&Recursive=true&Limit=0$filter")) as? JsonObject
        return root?.intOrNullAt("TotalRecordCount")
    }

    suspend fun scanAllLibraries() {
        send("POST", "$server/emby/Library/Refresh", "")
    }

    suspend fun refreshLibrary(id: String) {
        send("POST", "$server/emby/Items/${seg(id)}/Refresh?Recursive=true&ImageRefreshMode=Default&MetadataRefreshMode=Default&ReplaceAllImages=false&ReplaceAllMetadata=false", "")
    }

    suspend fun restartServer() {
        send("POST", "$server/emby/System/Restart", "")
    }

    // ----- GitHub Repo Plugin Install (the plugin manager's phone API) ---------------------------------------

    private val ghBase get() = "$server/emby/Plugins/GitHubRepoPluginInstall"

    suspend fun ghInfo(): GhrpiInfo = GhrpiParser.info(get("$ghBase/info"))

    suspend fun ghStatus(): GhStatusResponse = GhrpiParser.status(get("$ghBase/status"))

    suspend fun ghPlugins(): List<GhPlugin> = GhrpiParser.pluginList(get("$ghBase/plugins"))

    suspend fun ghPlugin(id: String): GhPlugin? = GhrpiParser.singlePlugin(get("$ghBase/plugins/${seg(id)}"))

    suspend fun ghHistory(limit: Int = 10): List<GhRun> = GhrpiParser.history(get("$ghBase/history?Limit=${limit.coerceIn(1, 20)}"))

    suspend fun ghRegistries(): List<GhRegistry> = GhrpiParser.registries(get("$ghBase/registries"))

    /** Plugins the enabled registries offer. Asks GitHub, so it can take a while. */
    suspend fun ghAvailable(): List<GhAvailable> = GhrpiParser.available(get("$ghBase/registries/plugins"))

    suspend fun ghSettings(): GhSettings = GhrpiParser.settings(get("$ghBase/settings"))

    suspend fun ghRun(mode: GhRunMode) {
        send("POST", "$ghBase/run", buildJsonObject { put("Mode", mode.name) }.toString())
    }

    suspend fun ghUpdate(id: String) {
        send("POST", "$ghBase/plugins/${seg(id)}/update", "")
    }

    suspend fun ghCheck(id: String) {
        send("POST", "$ghBase/plugins/${seg(id)}/check", "")
    }

    suspend fun ghRestore(id: String): GhActionResult = GhrpiParser.action(send("POST", "$ghBase/plugins/${seg(id)}/restore", ""))

    /** Only the fields given are sent, so only those change. */
    suspend fun ghPluginSettings(id: String, autoUpdate: Boolean? = null, allowPreRelease: Boolean? = null, assetNameFilter: String? = null): GhActionResult {
        val body = buildJsonObject {
            if (autoUpdate != null) put("AutoUpdate", autoUpdate)
            if (allowPreRelease != null) put("AllowPreRelease", allowPreRelease)
            if (assetNameFilter != null) put("AssetNameFilter", assetNameFilter)
        }
        return GhrpiParser.action(send("POST", "$ghBase/plugins/${seg(id)}/settings", body.toString()))
    }

    suspend fun ghAdd(address: String, autoUpdate: Boolean = true): GhActionResult {
        val body = buildJsonObject {
            put("Url", address.trim())
            put("AutoUpdate", autoUpdate)
        }
        return GhrpiParser.action(send("POST", "$ghBase/plugins", body.toString()))
    }

    suspend fun ghRemove(id: String): GhActionResult = GhrpiParser.action(send("DELETE", "$ghBase/plugins/${seg(id)}", null))

    suspend fun ghRestart() {
        send("POST", "$ghBase/restart", "")
    }

    suspend fun ghDismissRestart() {
        send("POST", "$ghBase/restart/dismiss", "")
    }

    /** The GitHub token is write-only: it goes to the plugin and is never read back. */
    suspend fun ghSaveToken(token: String): GhSettings = GhrpiParser.settings(send("POST", "$ghBase/settings", buildJsonObject { put("Token", token) }.toString()))

    suspend fun ghClearToken(): GhSettings = GhrpiParser.settings(send("POST", "$ghBase/settings", buildJsonObject { put("ClearToken", true) }.toString()))

    suspend fun ghSetRestartAfterInstall(on: Boolean): GhSettings =
        GhrpiParser.settings(send("POST", "$ghBase/settings", buildJsonObject { put("RestartServerAfterInstall", on) }.toString()))

    suspend fun ghTestToken(): Pair<Boolean, String> = GhrpiParser.tokenTest(send("POST", "$ghBase/settings/testtoken", ""))

    suspend fun ghSetRegistryEnabled(id: String, enabled: Boolean) {
        send("POST", "$ghBase/registries/${seg(id)}/enabled", buildJsonObject { put("Enabled", enabled) }.toString())
    }

    // ----- Hall Pass (explicit-content tagger for music; the plugin's own admin API, called with the signed-in administrator's token) -----

    private val hpBase get() = "$server/emby/HallPass"

    private fun hpUrl(path: String, params: Map<String, String?>): String {
        val b = "$hpBase/$path".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> if (!v.isNullOrEmpty()) b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    // ----- plugin alerts (the same routes on every plugin that follows docs/push-contract.md) ----------------

    /** What the plugin missed sending: events after [after], oldest first (at most 100). Throws on 404 from a plugin without alerts. */
    suspend fun pluginEvents(service: com.feerlessleadr.embidextrous.notifications.NotifService, after: Long, limit: Int = 100): String {
        val base = service.apiBase ?: throw ApiException(0, "${service.title} has no alerts API.")
        return get("$server$base/Events?After=${after.coerceAtLeast(0)}&Limit=${limit.coerceIn(1, 100)}")
    }

    /** The plugin's info answer, to see whether it can send alerts at all. */
    suspend fun pluginInfo(service: com.feerlessleadr.embidextrous.notifications.NotifService): String {
        val path = service.infoPath ?: throw ApiException(0, "${service.title} has no alerts API.")
        return get("$server$path")
    }

    /** The plugin's own list of alert types (titles, settings, defaults). */
    suspend fun pluginCatalog(service: com.feerlessleadr.embidextrous.notifications.NotifService): String {
        val base = service.apiBase ?: throw ApiException(0, "${service.title} has no alerts API.")
        return get("$server$base/Notifications/Catalog")
    }

    suspend fun hpInfo(): HpInfo = HallPassParser.info(get("$hpBase/Info"))

    suspend fun hpStatus(): HpStatus = HallPassParser.status(get("$hpBase/Status"))

    /** One page of the review list. [decision] is a comma separated list (Review, Explicit, LikelyClean, Clean, Skipped), empty for all. */
    suspend fun hpReview(decision: String, reason: String, text: String, artist: String?, album: String?, noAlbum: Boolean, sort: String, skip: Int, take: Int): Pair<Int, List<HpRow>> =
        HallPassParser.review(
            get(hpUrl("Review", mapOf("Decision" to decision, "Reason" to reason, "Text" to text, "Artist" to artist, "Album" to album, "NoAlbum" to if (noAlbum) "true" else null, "Sort" to sort, "Skip" to skip.toString(), "Take" to take.toString()))),
        )

    suspend fun hpAlbums(decision: String, reason: String, text: String, sort: String, skip: Int, take: Int): Pair<Int, List<HpAlbum>> =
        HallPassParser.albums(get(hpUrl("Review/Albums", mapOf("Decision" to decision, "Reason" to reason, "Text" to text, "Sort" to sort, "Skip" to skip.toString(), "Take" to take.toString()))))

    suspend fun hpArtists(decision: String, reason: String, text: String, sort: String, skip: Int, take: Int): Pair<Int, List<HpArtist>> =
        HallPassParser.artists(get(hpUrl("Review/Artists", mapOf("Decision" to decision, "Reason" to reason, "Text" to text, "Sort" to sort, "Skip" to skip.toString(), "Take" to take.toString()))))

    /** Sets (explicit or clean) or clears (clearOverride) the manual decision of these tracks. */
    suspend fun hpDecide(ids: List<Long>, decision: String): HpDecideResult =
        HallPassParser.decide(
            send("POST", "$hpBase/Items/Decide", buildJsonObject { put("Decision", decision); put("ItemIds", kotlinx.serialization.json.JsonArray(ids.map { JsonPrimitive(it) })) }.toString()),
        )

    /**
     * The same for everything that matches the filters, however many pages it spans. [artist] and [album] narrow it to one artist or one album: null
     * means any, and an empty [album] means the tracks that have no album.
     */
    suspend fun hpDecideMatching(decision: String, filterDecision: String, filterReason: String, filterText: String, artist: String?, album: String?): HpDecideResult =
        HallPassParser.decide(
            send(
                "POST", "$hpBase/Items/Decide",
                buildJsonObject {
                    put("Decision", decision)
                    put("AllMatching", true)
                    put("MatchDecision", filterDecision)
                    put("MatchReason", filterReason)
                    put("MatchText", filterText)
                    put("MatchArtist", artist ?: "")
                    put("MatchAlbum", album ?: "")
                    put("MatchNoAlbum", album != null && album.isEmpty())
                }.toString(),
            ),
        )

    suspend fun hpScan(force: Boolean) {
        send("POST", "$hpBase/Scan", buildJsonObject { put("Force", force) }.toString())
    }

    suspend fun hpCancelScan() {
        send("POST", "$hpBase/Scan/Cancel", "")
    }

    suspend fun hpPreview(): HpPreview = HallPassParser.preview(get("$hpBase/Apply/Preview"))

    suspend fun hpApply() {
        send("POST", "$hpBase/Apply", buildJsonObject { put("Confirm", true) }.toString())
    }

    // Ends this session on the server, so the token stops working there. Best effort: the caller ignores failure.
    suspend fun logout() {
        send("POST", "$server/emby/Sessions/Logout", "")
    }

    suspend fun users(): List<EmbyUser> =
        AppJson.parseToJsonElement(get("$pluginBase/users")).jsonArray.map {
            val o = it.jsonObject
            EmbyUser((o["Id"] as? JsonPrimitive)?.contentOrNull.orEmpty(), (o["Name"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }

    suspend fun libraries(): List<ValueOption> =
        AppJson.parseToJsonElement(get("$pluginBase/libraries")).jsonArray.map {
            val o = it.jsonObject
            val name = (o["Name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            ValueOption(name, name)
        }

    /** The fields a "similar to" rule can compare (from the older GET /fields, which the web page also uses). */
    suspend fun similarityFields(): List<ValueOption> {
        val root = AppJson.parseToJsonElement(get("$pluginBase/fields")).jsonObject
        return (root["SimilarityComparisonFields"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull {
            val o = it as? JsonObject ?: return@mapNotNull null
            ValueOption((o["Value"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null, (o["Label"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
    }

    // ----- custom images -------------------------------------------------------------------------------------

    suspend fun images(id: String): List<ImageInfo> =
        AppJson.parseToJsonElement(get("$pluginBase/${seg(id)}/images")).jsonArray.mapNotNull { ImageInfo.from(it as? JsonObject ?: return@mapNotNull null) }

    suspend fun uploadImage(id: String, imageType: String, fileName: String, mimeType: String, bytes: ByteArray): ImageInfo {
        val body = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("imageType", imageType)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val text = execute(Request.Builder().url("$pluginBase/${seg(id)}/images".toHttpUrl()).post(body))
        return ImageInfo.from(AppJson.parseToJsonElement(text).jsonObject)
    }

    suspend fun deleteImage(id: String, imageType: String) {
        send("DELETE", "$pluginBase/${seg(id)}/images/${seg(imageType)}", null)
    }

    /** The image file itself (the plugin serves it only to a signed-in administrator, so it needs the token header). */
    suspend fun imageBytes(id: String, imageType: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$pluginBase/${seg(id)}/images/${seg(imageType)}/file".toHttpUrl()).header("X-Emby-Token", token ?: throw ApiException(401, "You are signed out.")).get().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, describeError(response.code, ""))
                readLimited(response, Safety.MAX_IMAGE_BYTES) ?: ByteArray(0)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach the server: ${e.message ?: "network error"}.")
        }
    }

    /** Refreshes in progress and how each list's last refresh went. Best effort: an unfamiliar shape gives empty maps. */
    suspend fun refreshStatus(): RefreshStatus {
        val root = AppJson.parseToJsonElement(get("$pluginBase/Status")) as? JsonObject ?: return RefreshStatus.Empty
        fun text(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.contentOrNull
        val running = (root["ongoingOperations"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = text(o, "listId") ?: return@mapNotNull null
            id to RunningRefresh(o.intOrNullAt("processedItems") ?: 0, o.intOrNullAt("totalItems") ?: 0)
        }.toMap()
        // The history is ordered by recency: the newest entry of a list is the one that counts.
        val last = LinkedHashMap<String, RefreshOutcome>()
        (root["history"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .sortedByDescending { text(it, "endTime") ?: text(it, "startTime").orEmpty() }
            .forEach { o ->
                val id = text(o, "listId") ?: return@forEach
                if (id !in last) last[id] = RefreshOutcome((o["success"] as? JsonPrimitive)?.contentOrNull != "false", text(o, "errorMessage"), text(o, "endTime"))
            }
        return RefreshStatus(running, last)
    }

    // ----- preview -------------------------------------------------------------------------------------------

    /** What [doc] (a list as it would be saved) would contain right now. Needs plugin 0.1.10 or newer. */
    suspend fun preview(doc: JsonObject, limit: Int = 25): Preview {
        val root = AppJson.parseToJsonElement(send("POST", "$pluginBase/preview?limit=$limit", doc.toString())).jsonObject
        val items = (root["Items"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            PreviewItem(
                id = (o["Id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
                name = o.stringAt("Name").orEmpty(),
                type = o.stringAt("Type").orEmpty(),
                detail = o.stringAt("Detail"),
            )
        }
        return Preview(total = root.intOrNullAt("Total") ?: items.size, items = items)
    }

    // ----- cover images --------------------------------------------------------------------------------------

    /** The show an episode belongs to (its series id), or null for anything else or when Emby does not say. */
    suspend fun seriesIdOf(itemId: String): String? =
        ServerAdminParser.seriesId(get("$server/emby/Items?Ids=${seg(itemId)}&Fields=SeriesId&Limit=1"))

    /** The primary image of an Emby item (a list's playlist or collection), scaled by the server; null when it has none. */
    suspend fun embyCover(itemId: String, maxWidth: Int = 200): ByteArray? = withContext(Dispatchers.IO) {
        val url = "$server/emby/Items/${seg(itemId)}/Images/Primary?maxWidth=$maxWidth&quality=80".toHttpUrl()
        val request = Request.Builder().url(url).header("X-Emby-Token", token ?: throw ApiException(401, "You are signed out.")).get().build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null
                    !response.isSuccessful -> throw ApiException(response.code, describeError(response.code, ""))
                    else -> readLimited(response, Safety.MAX_COVER_BYTES)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach the server: ${e.message ?: "network error"}.")
        }
    }

    /** A user's profile picture, scaled by the server; null when the user has none. */
    suspend fun embyUserImage(userId: String, maxHeight: Int = 96): ByteArray? = withContext(Dispatchers.IO) {
        val url = "$server/emby/Users/${seg(userId)}/Images/Primary?maxHeight=$maxHeight&quality=90".toHttpUrl()
        val request = Request.Builder().url(url).header("X-Emby-Token", token ?: throw ApiException(401, "You are signed out.")).get().build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null
                    !response.isSuccessful -> throw ApiException(response.code, describeError(response.code, ""))
                    else -> readLimited(response, Safety.MAX_AVATAR_BYTES)
                }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach the server: ${e.message ?: "network error"}.")
        }
    }

    // ----- backups -------------------------------------------------------------------------------------------

    suspend fun backups(): List<BackupInfo> {
        val root = AppJson.parseToJsonElement(get("$pluginBase/backups")) as? JsonObject ?: return emptyList()
        return (root["backups"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            BackupInfo(
                filename = (o["filename"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                createdAt = (o["createdAt"] as? JsonPrimitive)?.contentOrNull,
                sizeBytes = (o["sizeBytes"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L,
                listCount = o.intOrNullAt("listCount") ?: 0,
            )
        }.sortedByDescending { it.createdAt.orEmpty() }
    }

    suspend fun createBackup(): String {
        val root = AppJson.parseToJsonElement(send("POST", "$pluginBase/backups", "")) as? JsonObject
        return (root?.get("message") as? JsonPrimitive)?.contentOrNull ?: "Backup created."
    }

    suspend fun deleteBackup(filename: String) {
        send("DELETE", "$pluginBase/backups/${seg(filename)}", null)
    }

    /** Restores the lists of a backup. [overwrite] replaces lists that already exist (same id); otherwise they are skipped. */
    suspend fun restoreBackup(filename: String, overwrite: Boolean): RestoreResult {
        val root = AppJson.parseToJsonElement(send("POST", "$pluginBase/backups/${seg(filename)}/restore?overwrite=$overwrite", "")).jsonObject
        return RestoreResult(root.intOrNullAt("restored") ?: 0, root.intOrNullAt("overwritten") ?: 0, root.intOrNullAt("skipped") ?: 0, root.intOrNullAt("errors") ?: 0)
    }

    // The backup file (a zip) copied straight into the given stream, so a large backup never sits in memory.
    suspend fun downloadBackupTo(filename: String, out: java.io.OutputStream) = withContext(Dispatchers.IO) {
        val url = "$pluginBase/backups/${seg(filename)}".toHttpUrl()
        val request = Request.Builder().url(url).header("X-Emby-Token", token ?: throw ApiException(401, "You are signed out.")).get().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code, describeError(response.code, ""))
                response.body?.byteStream()?.use { it.copyTo(out) }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach the server: ${e.message ?: "network error"}.")
        }
    }

    // The response body, refusing more than limit bytes (a hostile or broken server cannot make the app hold more).
    private fun readLimited(response: okhttp3.Response, limit: Long): ByteArray? {
        val body = response.body ?: return null
        val declared = body.contentLength()
        if (declared > limit) throw ApiException(0, "The server sent a larger picture than expected, so it was ignored.")
        val source = body.source()
        source.request(limit + 1)
        if (source.buffer.size > limit) throw ApiException(0, "The server sent a larger picture than expected, so it was ignored.")
        return source.readByteArray()
    }

    // A value taken from the server and put into a URL path: only letters, digits, dash, dot and underscore, and not
    // "." or "..", so it cannot add path segments, a query or a fragment.
    private fun seg(value: String): String {
        if (value.isEmpty() || value == "." || value == ".." || !SEGMENT.matches(value)) {
            throw ApiException(0, "The server sent an unexpected identifier, so the request was not made.")
        }
        return value
    }

    // ----- plumbing ------------------------------------------------------------------------------------------

    private suspend fun get(url: String): String = execute(Request.Builder().url(url.toHttpUrl()).get())

    private suspend fun send(method: String, url: String, json: String?): String {
        val body = (json ?: "").toRequestBody(JSON)
        val builder = Request.Builder().url(url.toHttpUrl())
        when (method) {
            "POST" -> builder.post(body)
            "PUT" -> builder.put(body)
            "DELETE" -> if (json == null) builder.delete() else builder.delete(body)
            else -> error("Unsupported method $method")
        }
        return execute(builder)
    }

    private suspend fun execute(builder: Request.Builder, authenticated: Boolean = true): String = withContext(Dispatchers.IO) {
        if (authenticated) {
            val t = token ?: throw ApiException(401, "You are signed out.")
            builder.header("X-Emby-Token", t)
        }
        builder.header("Accept", "application/json")
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, describeError(response.code, text), GhrpiParser.error(text)?.title)
                text
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: javax.net.ssl.SSLException) {
            throw ApiException(0, "Could not make a secure connection: ${e.message ?: "certificate problem"}.")
        } catch (e: IOException) {
            throw ApiException(0, "Could not reach the server: ${e.message ?: "network error"}.")
        }
    }

    private fun authorizationHeader(): String =
        "MediaBrowser Client=\"$appName\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$appVersion\""

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        const val SMARTLISTS_PLUGIN_ID = "7f0b8a52-6c1d-4b57-9a2e-5d3b0c4e91a8"
        private val SEGMENT = Regex("[A-Za-z0-9._-]+")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // The Emby API never redirects. Following one would re-send the X-Emby-Token header (OkHttp only strips
            // the standard Authorization header) to whatever host the redirect names, so a redirect is an error.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        /** Turns an error response into a sentence for a person. The plugin sends {title,status,detail}. */
        fun describeError(status: Int, body: String): String {
            val detail = try {
                AppJson.parseToJsonElement(body).jsonObject.let { o -> ((o["detail"] ?: o["Detail"]) as? JsonPrimitive)?.contentOrNull }
            } catch (e: Exception) {
                null
            }
            return when {
                !detail.isNullOrBlank() -> detail
                status in 300..399 -> "The server redirected the request (HTTP $status). The app does not follow redirects; check the server address."
                status == 401 -> "Your session has expired. Sign in again."
                status == 403 -> "This account is not an Emby administrator."
                status == 404 -> "Not found. Is the SmartLists plugin installed and up to date?"
                body.isNotBlank() && body.length < 200 && !body.trimStart().startsWith("<") -> body.trim()
                else -> "The server answered with HTTP $status."
            }
        }
    }
}
