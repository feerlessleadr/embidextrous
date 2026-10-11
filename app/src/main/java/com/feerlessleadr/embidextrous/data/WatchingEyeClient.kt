package com.feerlessleadr.embidextrous.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Talks to the Watching Eye plugin's key-protected routes (`/WatchingEye/Public/...`). Two credentials travel in headers, never
 * in a URL: the Emby sign-in token (`X-Emby-Token`, any account works, an administrator is not needed) and the plugin's own
 * key (`X-WatchingEye-Api-Key`).
 */
class WatchingEyeClient(
    serverUrl: String,
    private val token: String,
    private val pluginKey: String,
    private val http: OkHttpClient = ApiClient.defaultHttpClient(),
) {
    private val base = "${serverUrl.trimEnd('/')}/emby/WatchingEye/Public"

    suspend fun status(): List<UserStatus> {
        val text = call("GET", "Status", null, "Loading status")
        return AppJson.decodeFromString(ListSerializer(UserStatus.serializer()), text)
    }

    /** Adds extra time. */
    suspend fun extend(userId: String, minutes: Int) {
        call("POST", "ExtendTime", minutesBody(userId, minutes), "Add time")
    }

    /** Takes back extra time (the Undo for [extend]). Plugin 1.8 or newer. */
    suspend fun reduce(userId: String, minutes: Int) {
        call("POST", "ReduceTime", minutesBody(userId, minutes), "Undo")
    }

    suspend fun timeOut(userId: String, minutes: Int) {
        call("POST", "TimeOutUser", minutesBody(userId, minutes), "Time-out")
    }

    suspend fun clearTimeOut(userId: String) {
        call("POST", "ClearTimeOut", idBody(userId), "End time-out")
    }

    suspend fun resetTime(userId: String) {
        call("POST", "ResetUserTime", idBody(userId), "Reset")
    }

    /** Stops whatever the user is playing. Plugin 1.9 or newer (older plugins answer 404). */
    suspend fun stopStream(userId: String) {
        call("POST", "StopStream", idBody(userId), "Stop stream")
    }

    private fun idBody(userId: String) = buildJsonObject { put("UserId", userId) }.toString()

    private fun minutesBody(userId: String, minutes: Int): String {
        require(minutes in 1..MAX_MINUTES) { "Minutes must be between 1 and $MAX_MINUTES." }
        return buildJsonObject {
            put("UserId", userId)
            put("Minutes", minutes)
        }.toString()
    }

    private suspend fun call(method: String, path: String, json: String?, what: String): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$base/$path".toHttpUrl())
            .header("X-Emby-Token", token)
            .header("X-WatchingEye-Api-Key", pluginKey)
            .header("Accept", "application/json")
        if (method == "POST") builder.post((json ?: "").toRequestBody(JSON)) else builder.get()
        try {
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, WatchText.describeFailure(what, response.code))
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

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
