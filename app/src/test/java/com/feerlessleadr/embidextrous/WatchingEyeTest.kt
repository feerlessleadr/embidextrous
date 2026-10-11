package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import com.feerlessleadr.embidextrous.data.WatchingEyeClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

class WatchingEyeTest {
    private lateinit var server: MockWebServer
    private val now = Instant.parse("2026-10-10T12:00:00Z")

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun client() = WatchingEyeClient(server.url("/").toString().trimEnd('/'), "tok-123", "plugin-key-abc")

    private fun row(extra: String = "") =
        AppJson.decodeFromString(UserStatus.serializer(), """{"UserId":"u1","Username":"Kid","TimeOutUntil":"0001-01-01T00:00:00Z","BlockReason":"Allowed"$extra}""")

    // ---- parsing -----------------------------------------------------------------------------------------------

    @Test
    fun readsAPlugin19Row() {
        val u = row(""","SecondsRemaining":600.5,"LimitingPeriod":"Weekly","LimitingAllowedSeconds":7200,"ApiLevel":2,"IsEnabled":true,
            "NowPlaying":{"SessionId":"s9","ItemId":"77","ItemName":"Cartoon","ItemType":"Episode","RunTimeTicks":18000000000,"PositionTicks":6000000000,"IsPaused":true,"Client":"Emby for TV","DeviceName":"Living room"}""")
        assertTrue(u.reportsNowPlaying)
        assertEquals(600.5, u.remainingSeconds, 0.0001)
        assertEquals("weekly", u.periodLabel)
        assertEquals(7200.0, u.allowedSeconds, 0.0001)
        assertEquals("Cartoon", u.nowPlaying?.itemName)
        assertTrue(UserCard(u, 1000L).paused)
    }

    @Test
    fun anOlderPluginStillGivesARemainingTimeAndNoNowPlayingClaim() {
        val u = AppJson.decodeFromString(UserStatus.serializer(), """{"UserId":"u","Username":"K","DailyLimitMinutes":60,"SecondsWatchedDaily":600,"TimeOutUntil":"0001-01-01T00:00:00Z"}""")
        assertEquals(3000.0, u.remainingSeconds, 0.001)
        assertEquals("Allowed", u.block(now))
        assertFalse(u.reportsNowPlaying)
        val timedOut = AppJson.decodeFromString(UserStatus.serializer(), """{"UserId":"u","Username":"K","TimeOutUntil":"2999-01-01T00:00:00Z"}""")
        assertEquals("TimedOut", timedOut.block(now))
        assertNull(AppJson.decodeFromString(UserStatus.serializer(), """{"UserId":"u","TimeOutUntil":"2000-01-01T00:00:00Z"}""").activeTimeOutEnd(now))
    }

    @Test
    fun namesKeepQuotesAndAmpersands() {
        val u = AppJson.decodeFromString(UserStatus.serializer(), """{"UserId":"u","Username":"O'Brien & Co"}""")
        assertEquals("O'Brien & Co", u.username)
    }

    // ---- the countdown ----------------------------------------------------------------------------------------

    private fun playing(remaining: Int, reason: String = "Allowed", paused: Boolean = false) =
        row(""","SecondsRemaining":$remaining,"BlockReason":"$reason","ApiLevel":2,"NowPlaying":{"SessionId":"s","PositionTicks":100000000,"IsPaused":$paused}""")

    @Test
    fun theCountdownRunsWhileWatchingAndStopsWhenPausedBlockedOrIdle() {
        val t0 = 1_000_000L
        assertEquals(570.0, UserCard(playing(600), t0).remainingNow(t0 + 30_000), 0.001)
        assertEquals(600.0, UserCard(playing(600, paused = true), t0).remainingNow(t0 + 30_000), 0.001)
        assertEquals(600.0, UserCard(playing(600, reason = "TimedOut"), t0).remainingNow(t0 + 30_000), 0.001)
        assertEquals(600.0, UserCard(row(""","SecondsRemaining":600,"ApiLevel":2"""), t0).remainingNow(t0 + 30_000), 0.001)
        assertEquals(0.0, UserCard(playing(10), t0).remainingNow(t0 + 60_000), 0.001)
        assertEquals(-1.0, UserCard(playing(-1), t0).remainingNow(t0 + 60_000), 0.001)
    }

    @Test
    fun thePositionAdvancesOnlyWhilePlaying() {
        val t0 = 5_000_000L
        assertEquals(100_000_000L + 20_000L * 10_000L, UserCard(playing(600), t0).positionTicksNow(t0 + 20_000))
        assertEquals(100_000_000L, UserCard(playing(600, paused = true), t0).positionTicksNow(t0 + 20_000))
    }

    // ---- texts -------------------------------------------------------------------------------------------------

    @Test
    fun formatsDurationsClocksAndPeriods() {
        assertEquals("45 s", WatchText.duration(45.9))
        assertEquals("0 s", WatchText.duration(-5.0))
        assertEquals("9 min 30 s", WatchText.duration(570.0))
        assertEquals("42 min", WatchText.duration(42 * 60 + 59.0))
        assertEquals("1 h 05 min", WatchText.duration(3900.0))
        assertEquals("7:05", WatchText.clock((7 * 60 + 5) * 10_000_000L))
        assertEquals("1:02:03", WatchText.clock((3600 + 2 * 60 + 3) * 10_000_000L))
        assertEquals("this week", WatchText.periodPhrase("Weekly"))
        assertEquals("today", WatchText.periodPhrase(null))
    }

    @Test
    fun explainsTheCommonPluginRefusals() {
        assertTrue(WatchText.describeFailure("Extend", 400).contains("1-43200"))
        assertTrue(WatchText.describeFailure("Extend", 403).contains("Enable Public API"))
        assertTrue(WatchText.describeFailure("Status", 404).contains("plugin installed"))
        assertTrue(WatchText.describeFailure("Status", 500).contains("HTTP 500"))
    }

    // ---- the client ---------------------------------------------------------------------------------------------

    @Test
    fun statusSendsBothKeysInHeadersOnly() = runTest {
        server.enqueue(MockResponse().setBody("""[{"UserId":"u1","Username":"Kid","SecondsRemaining":60,"ApiLevel":2}]"""))
        val rows = client().status()
        assertEquals(1, rows.size)
        val request = server.takeRequest()
        assertEquals("/emby/WatchingEye/Public/Status", request.path)
        assertEquals("tok-123", request.getHeader("X-Emby-Token"))
        assertEquals("plugin-key-abc", request.getHeader("X-WatchingEye-Api-Key"))
        assertFalse(request.path!!.contains("tok-123"))
        assertFalse(request.path!!.contains("plugin-key-abc"))
    }

    @Test
    fun theActionsPostTheRightBodies() = runTest {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(204)) }
        val c = client()
        c.extend("u1", 15)
        c.reduce("u1", 15)
        c.timeOut("u1", 30)
        c.clearTimeOut("u1")
        c.resetTime("u1")
        c.stopStream("u1")
        val paths = (1..6).map { server.takeRequest() }
        assertEquals(
            listOf("ExtendTime", "ReduceTime", "TimeOutUser", "ClearTimeOut", "ResetUserTime", "StopStream").map { "/emby/WatchingEye/Public/$it" },
            paths.map { it.path },
        )
        assertTrue(paths[0].body.readUtf8().let { it.contains("\"UserId\":\"u1\"") && it.contains("\"Minutes\":15") })
        assertEquals("""{"UserId":"u1"}""", paths[5].body.readUtf8())
    }

    @Test
    fun minutesOutsideTheLimitsAreRefusedBeforeAnyRequest() = runTest {
        for (bad in listOf(0, -1, 43201)) {
            try {
                client().extend("u1", bad)
                fail("accepted $bad")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun httpErrorsBecomeSentences() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        try {
            client().status()
            fail("expected a failure")
        } catch (e: ApiException) {
            assertEquals(403, e.status)
            assertTrue(e.message!!.contains("Enable Public API"))
        }
    }

    @Test
    fun aRedirectIsNotFollowedSoTheKeysNeverLeaveTheServer() = runTest {
        val other = MockWebServer()
        other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/stolen").toString()))
            try {
                client().status()
                fail("expected a failure")
            } catch (e: ApiException) {
                assertEquals(307, e.status)
            }
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }
}
