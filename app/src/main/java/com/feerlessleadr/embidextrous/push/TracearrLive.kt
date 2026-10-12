package com.feerlessleadr.embidextrous.push

import com.feerlessleadr.embidextrous.data.TracearrConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the live event stream tells the rest of the app. */
interface TracearrLiveListener {
    /** The stream is open (true) or has ended (false). */
    fun onConnected(connected: Boolean)

    /** The first frame of every connection: fetch the starting state over REST now, later events apply to it. */
    fun onReady()

    /** One event: "stream.started", "stream.updated", "stream.progress", "stream.stopped", "violation.created" or "server.health", with its JSON text. */
    fun onEvent(type: String, data: String)

    /** The server will not take the key (wrong, revoked or without an owner): stop trying until the connection is changed. */
    fun onKeyRejected()
}

/**
 * Tracearr's live events (server-sent events, GET /api/v2/public/events): one connection that Tracearr pushes each event down, so the app does not poll.
 * Nothing is replayed after a disconnect, so every new connection starts with a "ready" frame and the app fetches the state again. The stream ends by
 * itself every 30 minutes; this reconnects straight away then, and with a growing delay (and Tracearr's own "retry:") after a failure or a 429.
 * The key goes in the Authorization header, the connection is HTTPS only (like every Tracearr call), and nothing is stored.
 */
class TracearrLive(private val connection: TracearrConnection, http: OkHttpClient) {
    // Tracearr sends a heartbeat comment every 25 s, so 70 s of silence means the connection is dead.
    private val client = http.newBuilder()
        .readTimeout(70, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** Runs until the calling coroutine is cancelled (or the key is rejected). */
    suspend fun run(listener: TracearrLiveListener) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val outcome = withContext(Dispatchers.IO) { readOnce(listener) }
            listener.onConnected(false)
            when (outcome) {
                is Outcome.Rejected -> {
                    listener.onKeyRejected()
                    return
                }
                is Outcome.Ended -> {
                    // A normal end (the 30 minute limit): come straight back. A connection that never opened counts as a failure.
                    if (outcome.opened) failures = 0 else failures++
                    delay(if (outcome.opened) 500 else backoff(failures, outcome.retryMs))
                }
                is Outcome.Limited -> {
                    failures++
                    delay(maxOf(outcome.retryAfterMs, backoff(failures, null)))
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Ended(val opened: Boolean, val retryMs: Long?) : Outcome
        data object Rejected : Outcome
        data class Limited(val retryAfterMs: Long) : Outcome
    }

    private fun backoff(failures: Int, serverRetryMs: Long?): Long =
        maxOf(serverRetryMs ?: 5_000L, BACKOFF_MS[minOf(maxOf(failures, 1) - 1, BACKOFF_MS.size - 1)])

    private suspend fun readOnce(listener: TracearrLiveListener): Outcome {
        val url = try {
            (connection.url.trimEnd('/') + "/api/v2/public/events").toHttpUrl()
        } catch (e: IllegalArgumentException) {
            return Outcome.Ended(opened = false, retryMs = null)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${connection.key}")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = client.newCall(request)
        val parser = SseParser()
        var opened = false
        // A blocked read ends as soon as the caller is cancelled (a screen leaving the foreground).
        val onCancel = currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
        return try {
            runInterruptible { call.execute() }.use { response ->
                when {
                    response.code == 401 || response.code == 403 -> return Outcome.Rejected
                    response.code == 429 -> return Outcome.Limited((response.header("Retry-After")?.toLongOrNull() ?: 30L).coerceIn(1, 600) * 1000L)
                    !response.isSuccessful -> return Outcome.Ended(opened = false, retryMs = null)
                }
                val source = response.body?.source() ?: return Outcome.Ended(opened = false, retryMs = null)
                opened = true
                listener.onConnected(true)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    val event = parser.feed(line) ?: continue
                    if (event.name == "ready") listener.onReady() else listener.onEvent(event.name, event.data)
                }
                Outcome.Ended(opened, parser.retryMs)
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: IOException) {
            Outcome.Ended(opened, parser.retryMs)
        } finally {
            onCancel?.dispose()
        }
    }

    private companion object {
        val BACKOFF_MS = longArrayOf(5_000, 10_000, 20_000, 40_000, 60_000)
    }
}
