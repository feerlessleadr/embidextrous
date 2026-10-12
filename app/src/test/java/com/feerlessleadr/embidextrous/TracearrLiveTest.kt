package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.ApiClient
import com.feerlessleadr.embidextrous.data.TracearrConnection
import com.feerlessleadr.embidextrous.push.TracearrLive
import com.feerlessleadr.embidextrous.push.TracearrLiveListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

/** The live event stream against a scripted server: what it sends, how it reconnects, what it does when the key is refused. */
class TracearrLiveTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private class Recorder : TracearrLiveListener {
        val log: MutableList<String> = Collections.synchronizedList(ArrayList())
        val rejected = CompletableDeferred<Unit>()
        val twoReadies = CompletableDeferred<Unit>()
        override fun onConnected(connected: Boolean) { log.add("connected=$connected") }
        override fun onReady() {
            log.add("ready")
            if (log.count { it == "ready" } >= 2) twoReadies.complete(Unit)
        }
        override fun onEvent(type: String, data: String) { log.add("event:$type") }
        override fun onKeyRejected() { rejected.complete(Unit) }
    }

    private fun live() = TracearrLive(TracearrConnection(server.url("/").toString().trimEnd('/'), "trr_pub_testkey"), ApiClient.defaultHttpClient())

    private val stream = "retry: 100\n\nevent: ready\ndata: {\"type\":\"ready\"}\n\n: ping\n\nevent: stream.started\ndata: {\"type\":\"stream.started\"}\n\nevent: violation.created\ndata: {}\n\n"

    @Test fun readsReadyThenEventsAndSendsTheKeyInTheHeaderOnly() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream)) // the reconnect
        val rec = Recorder()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launchRun(live(), rec)
        withTimeout(10_000) { rec.twoReadies.await() }
        job.cancel()
        val first = server.takeRequest()
        assertEquals("Bearer trr_pub_testkey", first.getHeader("Authorization"))
        assertEquals("text/event-stream", first.getHeader("Accept"))
        assertEquals("/api/v2/public/events", first.path) // no key in the address
        val order = rec.log.toList()
        assertEquals(listOf("connected=true", "ready", "event:stream.started", "event:violation.created", "connected=false"), order.take(5))
        // the stream ended (as it does every 30 minutes), so it came back by itself
        assertTrue(order.count { it == "ready" } >= 2)
    }

    @Test fun aRefusedKeyStopsTryingAndSaysSo() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Invalid or missing API key\"}"))
        val rec = Recorder()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launchRun(live(), rec)
        withTimeout(10_000) { rec.rejected.await() }
        job.join() // run() returns by itself after a rejection
        assertEquals(1, server.requestCount)
        assertTrue(rec.log.none { it == "ready" })
    }

    @Test fun aRateLimitIsWaitedOutNotHammered() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(stream))
        val rec = Recorder()
        val started = System.currentTimeMillis()
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launchRun(live(), rec)
        withTimeout(20_000) { while (rec.log.none { it == "ready" }) kotlinx.coroutines.delay(50) }
        job.cancel()
        assertTrue("came back after only ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started >= 900)
        assertEquals(2, server.requestCount)
    }

    private fun kotlinx.coroutines.CoroutineScope.launchRun(live: TracearrLive, listener: TracearrLiveListener): kotlinx.coroutines.Job = this.launch { live.run(listener) }
}
