package com.feerlessleadr.embidextrous.watching

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** What the live connection tells the rest of the app. */
interface LiveListener {
    fun onStatus(users: List<UserStatus>, atMs: Long)
    fun onState(state: LiveState, detail: String)
}

enum class LiveState { CONNECTING, LIVE, RECONNECTING, REFUSED }

/** Reading and writing the messages on Emby's websocket. Pure functions, covered by unit tests. */
object LiveMessages {
    sealed interface Message {
        data class Status(val users: List<UserStatus>) : Message
        data class Refused(val reason: String) : Message
        data class KeepAliveRequest(val seconds: Int) : Message
        data object Other : Message
    }

    /** Sent right after connecting. The plugin key travels in the message body, never in the URL. */
    fun subscribe(pluginKey: String): String = buildJsonObject {
        put("MessageType", "WatchingEyeStatusStart")
        put("Data", "0,30000,$pluginKey")
    }.toString()

    fun keepAlive(): String = """{"MessageType":"KeepAlive"}"""

    fun parse(text: String): Message = try {
        val root = AppJson.parseToJsonElement(text) as? JsonObject
        val type = (root?.get("MessageType") as? JsonPrimitive)?.contentOrNull.orEmpty()
        val data = root?.get("Data")
        when (type) {
            "WatchingEyeStatus" -> {
                val obj = data as? JsonObject
                val error = (obj?.get("Error") as? JsonPrimitive)?.contentOrNull
                if (error != null) {
                    Message.Refused(error)
                } else {
                    val users = obj?.get("Users")?.let { AppJson.decodeFromJsonElement(ListSerializer(UserStatus.serializer()), it) }.orEmpty()
                    Message.Status(users)
                }
            }
            "ForceKeepAlive" -> Message.KeepAliveRequest((data as? JsonPrimitive)?.intOrNull ?: 0)
            else -> Message.Other
        }
    } catch (e: Exception) {
        Message.Other
    }

    /** http(s)://host[:port][/base] to the Emby websocket address. The token goes in a header, not here. */
    fun url(serverUrl: String, deviceId: String): String = serverUrl.trimEnd('/') + "/embywebsocket?deviceId=" + deviceId
}

/**
 * The live connection to Emby's websocket: the Watching Eye plugin pushes the status when something changes, so the app never
 * has to ask. It reconnects by itself with a growing delay, and treats a long silence (the plugin sends at least every 30 s) as a
 * dead connection, which is what happens after a phone has slept.
 */
class LiveConnection(
    private val serverUrl: String,
    private val token: String,
    private val pluginKey: String,
    private val deviceId: String,
    http: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // A websocket stays open for hours: no read or call timeout, and a ping every 30 s keeps routers and proxies from dropping it.
    private val client = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val currentSocket = AtomicReference<WebSocket?>(null)
    private val lastMessageMs = AtomicLong(0L)

    /** Connects and stays connected until the calling coroutine is cancelled. */
    suspend fun run(listener: LiveListener) = coroutineScope {
        val watchdog = launch {
            while (isActive) {
                delay(WATCHDOG_MS)
                val last = lastMessageMs.get()
                if (last != 0L && clock() - last > SILENCE_LIMIT_MS) currentSocket.get()?.cancel()
            }
        }
        var failures = 0
        listener.onState(LiveState.CONNECTING, "")
        try {
            while (isActive) {
                val outcome = connectOnce(listener)
                if (!isActive) break
                when (outcome) {
                    is Outcome.Refused -> {
                        failures = 0
                        listener.onState(LiveState.REFUSED, outcome.reason)
                        delay(REFUSED_RETRY_MS)
                    }
                    is Outcome.Lost -> {
                        if (outcome.hadData) failures = 0
                        val wait = BACKOFF_MS[minOf(failures, BACKOFF_MS.size - 1)]
                        failures++
                        listener.onState(LiveState.RECONNECTING, outcome.detail)
                        delay(wait)
                    }
                }
            }
        } finally {
            watchdog.cancel()
            currentSocket.getAndSet(null)?.cancel()
        }
    }

    private sealed interface Outcome {
        data class Refused(val reason: String) : Outcome
        data class Lost(val detail: String, val hadData: Boolean) : Outcome
    }

    private suspend fun connectOnce(listener: LiveListener): Outcome = suspendCancellableCoroutine { cont ->
        val done = AtomicBoolean(false)
        var gotData = false
        var keepAlive: Job? = null
        val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

        fun finish(outcome: Outcome) {
            if (done.compareAndSet(false, true)) {
                keepAlive?.cancel()
                scope.coroutineContext[Job]?.cancel()
                currentSocket.set(null)
                if (cont.isActive) cont.resume(outcome)
            }
        }

        val request = Request.Builder()
            .url(LiveMessages.url(serverUrl, deviceId))
            .header("X-Emby-Token", token)
            .build()
        lastMessageMs.set(clock())
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(LiveMessages.subscribe(pluginKey))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastMessageMs.set(clock())
                when (val message = LiveMessages.parse(text)) {
                    is LiveMessages.Message.Status -> {
                        gotData = true
                        listener.onState(LiveState.LIVE, "")
                        listener.onStatus(message.users, clock())
                    }
                    is LiveMessages.Message.Refused -> {
                        webSocket.cancel()
                        finish(Outcome.Refused(message.reason))
                    }
                    is LiveMessages.Message.KeepAliveRequest -> {
                        keepAlive?.cancel()
                        val period = maxOf(10, if (message.seconds > 0) message.seconds / 2 else 30) * 1000L
                        keepAlive = scope.launch {
                            while (isActive) {
                                delay(period)
                                webSocket.send(LiveMessages.keepAlive())
                            }
                        }
                        webSocket.send(LiveMessages.keepAlive())
                    }
                    LiveMessages.Message.Other -> Unit
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(Outcome.Lost("The connection was closed ($code).", gotData))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val what = if (response != null) "HTTP ${response.code}" else describe(t)
                finish(Outcome.Lost(what, gotData))
            }
        })
        currentSocket.set(socket)
        cont.invokeOnCancellation {
            done.set(true)
            keepAlive?.cancel()
            scope.coroutineContext[Job]?.cancel()
            socket.cancel()
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is java.net.UnknownHostException -> "Can't find the server (${t.javaClass.simpleName}). Check the address and your connection."
        is java.net.SocketTimeoutException -> "The server is not answering (${t.javaClass.simpleName})."
        is javax.net.ssl.SSLException -> "Secure connection failed (${t.javaClass.simpleName})."
        else -> "Can't reach the server (${t.javaClass.simpleName})."
    }

    companion object {
        /** The plugin pushes at least every 30 s; this long with nothing at all means the connection is dead. */
        const val SILENCE_LIMIT_MS = 75_000L
        private const val WATCHDOG_MS = 15_000L
        // After a refusal (usually a wrong key) wait as long as the plugin's lockout window, so retrying can never trip the lockout.
        private const val REFUSED_RETRY_MS = KeyGuard.BLOCK_MS
        private val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000, 60_000)
    }
}
