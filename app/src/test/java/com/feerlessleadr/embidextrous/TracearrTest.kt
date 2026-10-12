package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.TracearrParser
import com.feerlessleadr.embidextrous.data.TracearrRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tracearr's public API v2 answers, written as its OpenAPI definition shows them. */
class TracearrTest {
    @Test fun parsesHistoryAndItsCursor() {
        val text = """{"data":[
            {"id":"p1","server_id":"s","server_name":"Attic","server_type":"emby","state":"stopped","media_type":"episode","media_title":"Pilot","show_title":"A Show",
             "season_number":2,"episode_number":5,"year":null,"artist_name":null,"poster_url":"/api/v1/images/proxy?x=1","duration_ms":1500000,"total_duration_ms":1560000,
             "percent_complete":95.8,"started_at":"2026-10-10T10:00:00.000Z","stopped_at":null,"watched":true,"device":"Apple TV","platform":"tvOS","is_transcode":false,"resolution":"4K",
             "user":{"id":"u1","server_user_id":"su1","username":"Sam","thumb_url":null,"avatar_url":null},"future_field":1},
            {"id":"p2","media_title":"Film","media_type":"movie","year":1999,"user":{"id":"u2","username":"Kim"}}
        ],"meta":{"nextCursor":"abc","pageSize":25}}"""
        val page = TracearrParser.history(text)
        assertEquals(2, page.items.size)
        assertEquals("abc", page.nextCursor)
        val p = page.items[0]
        assertEquals("A Show · S2E5", p.subtitle)
        assertEquals("Sam", p.username)
        assertTrue(p.watched)
        assertEquals(95.8, p.percentComplete!!, 0.001)
        assertEquals(1_500_000L, p.watchedMs)
        assertNotNull(p.startedMs)
        assertEquals("1999", page.items[1].subtitle)
        assertEquals(0L, page.items[1].watchedMs)
        assertNull(TracearrParser.history("""{"data":[],"meta":{"nextCursor":null,"pageSize":25}}""").nextCursor)
    }

    @Test fun parsesStreamsAndTheirSummary() {
        val text = """{"data":[{"id":"s1","server_name":"Attic","username":"Sam","media_title":"Inception","year":2010,"state":"paused","progress_ms":600000,"duration_ms":8880000,
            "is_transcode":true,"video_decision":"transcode","resolution":"4K","bitrate":20000,"device":"TV","platform":"tvOS"}],
            "summary":{"total":1,"transcodes":1,"audio_transcodes":0,"direct_streams":0,"direct_plays":0,"total_bitrate":"20.0 Mbps","by_server":[]}}"""
        val s = TracearrParser.streams(text)
        assertEquals(1, s.streams.size)
        assertTrue(s.streams[0].paused)
        assertTrue(s.streams[0].transcode)
        assertEquals("2010", s.streams[0].subtitle)
        assertEquals(1, s.summary.transcodes)
        assertEquals("20.0 Mbps", s.summary.totalBitrate)
        // the summary-only form has no data array
        assertTrue(TracearrParser.streams("""{"summary":{"total":0,"transcodes":0,"direct_streams":0,"direct_plays":0,"total_bitrate":"—"}}""").streams.isEmpty())
    }

    @Test fun parsesViolationsWithTheirEvidence() {
        val text = """{"data":[{"id":"v1","severity":"high","created_at":"2026-10-06T10:00:05.000Z","acknowledged_at":null,"session_id":"s",
            "rule":{"id":"r","name":"Too many streams"},"server":{"id":"s","name":"Attic","type":"plex"},
            "user":{"id":"u","server_user_id":"su","username":"Alice","thumb_url":null,"avatar_url":null},
            "data":{"ruleName":"Too many streams","mediaTitle":"Inception","ipAddress":"203.0.113.9","evidence":[]}},
            {"id":"v2","severity":"low","created_at":"2026-10-05T10:00:05.000Z","acknowledged_at":"2026-10-06T10:00:05.000Z","rule":{"id":"r","name":"Odd place"},"user":{"id":"u","username":"Bob"},"data":{}}],
            "meta":{"nextCursor":null,"pageSize":25}}"""
        val v = TracearrParser.violations(text).items
        assertEquals(2, v.size)
        assertEquals("Too many streams", v[0].ruleName)
        assertEquals("Inception", v[0].mediaTitle)
        assertEquals("203.0.113.9", v[0].ipAddress)
        assertFalse(v[0].acknowledged)
        assertTrue(v[1].acknowledged)
        assertNull(v[1].mediaTitle)
    }

    @Test fun parsesServersUsersAndStats() {
        val servers = TracearrParser.servers("""{"data":[{"server_id":"s","server_name":"Attic","status":"up","reason":null,"server_type":"emby","historical":false,"active_streams":2,"version":"4.10"}],"tracearr_version":"2.7.0"}""")
        assertEquals("2.7.0", servers.tracearrVersion)
        assertEquals("up", servers.servers[0].status)
        assertEquals(2, servers.servers[0].activeStreams)
        val users = TracearrParser.users("""{"data":[{"id":"u","username":"Sam","email":null,"plex_account_id":null,"accounts":[{"server_id":"s","server_type":"emby","username":"sam","removed_at":null},{"server_type":"plex","username":"old","removed_at":"2026-01-01T00:00:00Z"}]}],"meta":{"nextCursor":null,"pageSize":100}}""")
        assertEquals(2, users.items[0].accounts.size)
        assertTrue(users.items[0].accounts[1].removed)
        val stats = TracearrParser.userStats("""{"user_id":"u","windows":{"all_time":{"plays":10,"watch_time_ms":36000000},"last_30":{"plays":4,"watch_time_ms":7200000},"last_7":{"plays":1,"watch_time_ms":1800000}},"top_genres":[{"genre":"Action","plays":5},{"genre":"Drama","plays":2}]}""")!!
        assertEquals(10, stats.allTime.plays)
        assertEquals(1_800_000L, stats.last7.watchTimeMs)
        assertEquals(listOf("Action" to 5, "Drama" to 2), stats.topGenres)
    }

    @Test fun garbageGivesEmptyResultsNotCrashes() {
        assertTrue(TracearrParser.history("nope").items.isEmpty())
        assertTrue(TracearrParser.streams("[]").streams.isEmpty())
        assertTrue(TracearrParser.violations("").items.isEmpty())
        assertTrue(TracearrParser.servers("{}").servers.isEmpty())
        assertNull(TracearrParser.userStats("nope"))
        assertEquals("Slow down", TracearrParser.errorMessage("""{"message":"Slow down"}"""))
        assertNull(TracearrParser.errorMessage("<html>"))
    }

    @Test fun keysAreCheckedBeforeSaving() {
        assertNull(TracearrRules.checkKey("trr_pub_" + "a".repeat(30)))
        assertNotNull(TracearrRules.checkKey(""))
        assertNotNull(TracearrRules.checkKey("sk-something-else-entirely-1234567890"))
        assertNotNull(TracearrRules.checkKey("trr_pub_x"))
        assertNotNull(TracearrRules.checkKey("trr_pub_" + "a".repeat(30) + " b"))
        assertNotNull(TracearrRules.checkKey("trr_pub_" + "a".repeat(400)))
    }

    @Test fun onlyPathsOnTheTracearrServerAreLoadedAsPictures() {
        assertTrue(TracearrRules.isOwnPath("/api/v1/images/proxy?serverId=1&path=%2Fa"))
        assertFalse(TracearrRules.isOwnPath("https://evil.example.com/a.png"))
        assertFalse(TracearrRules.isOwnPath("//evil.example.com/a.png"))
        assertFalse(TracearrRules.isOwnPath("/../../etc/passwd"))
        assertFalse(TracearrRules.isOwnPath(null))
        assertFalse(TracearrRules.isOwnPath("a".repeat(3000)))
    }
}
