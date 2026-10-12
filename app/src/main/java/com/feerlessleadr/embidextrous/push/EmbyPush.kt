package com.feerlessleadr.embidextrous.push

import com.feerlessleadr.embidextrous.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** A hold on some push topics. Release it (it is safe to release twice) when the screen that needed them goes away. */
fun interface PushLease {
    fun release()
}

/**
 * One shared connection to Emby's websocket for everything the app wants pushed (the live sessions, the scheduled tasks, the activity log, and whatever
 * the server and its plugins send on their own). Screens take a lease on the topics they need; the connection is open only while at least one lease exists,
 * it switches each topic on and off as leases come and go, and it reconnects by itself with a growing delay. [connected] says whether pushes are flowing,
 * so a screen can fall back to asking now and then only while they are not.
 */
class EmbyPush(
    private val scope: CoroutineScope,
    private val sessionProvider: () -> Session?,
    private val deviceId: () -> String,
    http: OkHttpClient,
) {
    // A websocket stays open for hours: no read or call timeout, and a ping every 30 s keeps routers and proxies from dropping it.
    private val client = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val lock = Any()
    private val holds = HashMap<EmbyTopic, Int>()
    private var loop: Job? = null
    private var socket: WebSocket? = null
    private val seenTypes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val _connected = MutableStateFlow(false)

    /** True while the websocket is open and the topics are switched on. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _events = MutableSharedFlow<PushEvent>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Every message Emby sends, whatever its type; collectors pick the ones they want. */
    val events: SharedFlow<PushEvent> = _events.asSharedFlow()

    /** Holds [topics] open until the returned lease is released. */
    fun acquire(vararg topics: EmbyTopic): PushLease {
        val mine = topics.toList()
        synchronized(lock) {
            for (t in mine) {
                val before = holds.getOrDefault(t, 0)
                holds[t] = before + 1
                if (before == 0) socket?.let { s -> EmbyMessages.start(t)?.let { s.send(it) } }
            }
            if (loop == null) loop = scope.launch { run() }
        }
        var released = false
        return PushLease {
            synchronized(lock) {
                if (released) return@synchronized
                released = true
                for (t in mine) {
                    val left = holds.getOrDefault(t, 0) - 1
                    if (left <= 0) {
                        holds.remove(t)
                        socket?.let { s -> EmbyMessages.stop(t)?.let { s.send(it) } }
                    } else {
                        holds[t] = left
                    }
                }
                if (holds.isEmpty()) {
                    loop?.cancel()
                    loop = null
                    socket?.cancel()
                    socket = null
                    _connected.value = false
                }
            }
        }
    }

    private suspend fun run() {
        var failures = 0
        while (scope.isActive) {
            val session = sessionProvider()
            if (session == null) {
                delay(5_000)
                continue
            }
            val hadData = connectOnce(session)
            _connected.value = false
            if (hadData) failures = 0
            delay(BACKOFF_MS[minOf(failures, BACKOFF_MS.size - 1)])
            failures++
        }
    }

    /** One connection, until it ends. Returns whether it was up long enough to count as working. */
    private suspend fun connectOnce(session: Session): Boolean = suspendCancellableCoroutine { cont ->
        var worked = false
        var keepAlive: Job? = null
        val request = Request.Builder()
            .url(EmbyMessages.url(session.serverUrl, deviceId()))
            .header("X-Emby-Token", session.token)
            .build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    socket = webSocket
                    for (t in holds.keys) EmbyMessages.start(t)?.let { webSocket.send(it) }
                }
                worked = true
                _connected.value = true
                if (com.feerlessleadr.embidextrous.BuildConfig.DEBUG) android.util.Log.d("EmbyPush", "connected; topics " + synchronized(lock) { holds.keys.toList() })
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = EmbyMessages.parse(text) ?: return
                if (com.feerlessleadr.embidextrous.BuildConfig.DEBUG && seenTypes.add(event.type)) android.util.Log.d("EmbyPush", "first message of type " + event.type + ": " + text.take(160))
                EmbyMessages.keepAliveSeconds(event)?.let { seconds ->
                    keepAlive?.cancel()
                    val period = maxOf(10, if (seconds > 0) seconds / 2 else 30) * 1000L
                    keepAlive = scope.launch {
                        while (isActive) {
                            delay(period)
                            webSocket.send(EmbyMessages.keepAlive())
                        }
                    }
                    webSocket.send(EmbyMessages.keepAlive())
                }
                _events.tryEmit(event)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                keepAlive?.cancel()
                synchronized(lock) { if (socket === webSocket) socket = null }
                if (cont.isActive) cont.resume(worked)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                keepAlive?.cancel()
                synchronized(lock) { if (socket === webSocket) socket = null }
                if (cont.isActive) cont.resume(worked)
            }
        })
        cont.invokeOnCancellation {
            keepAlive?.cancel()
            ws.cancel()
        }
    }

    private companion object {
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000, 30_000, 60_000)
    }
}
