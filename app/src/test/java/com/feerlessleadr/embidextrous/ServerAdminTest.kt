package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.ActivityKind
import com.feerlessleadr.embidextrous.data.PlayMethod
import com.feerlessleadr.embidextrous.data.ServerAdminParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAdminTest {
    @Test fun parsesAPlayingAndAnIdleSession() {
        val text = """[
            {"Id":"s1","UserId":"user-1","UserName":"Sam","Client":"Emby Web","DeviceName":"Chrome","ApplicationVersion":"4.10","RemoteEndPoint":"10.0.0.5","LastActivityDate":"2026-10-10T11:59:00Z",
             "NowPlayingItem":{"Id":"77","Name":"Pilot","Type":"Episode","SeriesName":"A Show","ParentIndexNumber":2,"IndexNumber":5,"RunTimeTicks":27000000000},
             "PlayState":{"PositionTicks":9000000000,"IsPaused":true,"PlayMethod":"Transcode"},
             "TranscodingInfo":{"VideoCodec":"h264","AudioCodec":"aac","TranscodeReasons":["VideoCodecNotSupported","ContainerNotSupported"]}},
            {"Id":"s2","Client":"Emby Server DLNA","DeviceName":"Speaker"}
        ]"""
        val sessions = ServerAdminParser.sessions(text)
        assertEquals(2, sessions.size)
        val p = sessions[0].playing!!
        assertEquals("Pilot", p.title)
        assertEquals("77", p.itemId)
        assertEquals("user-1", sessions[0].userId)
        assertEquals("A Show · S2E5", p.subtitle)
        assertEquals(PlayMethod.Transcode, p.method)
        assertTrue(p.paused)
        assertEquals("h264 + aac", p.codecs)
        assertEquals(listOf("VideoCodecNotSupported", "ContainerNotSupported"), p.reasons)
        assertEquals(9_000_000_000L, p.positionTicks)
        assertNull(sessions[1].playing)
        assertEquals("", sessions[1].userName)
    }

    @Test fun aMovieShowsItsYearAndDirectPlayIsRecognised() {
        val text = """[{"Id":"s","UserName":"A","NowPlayingItem":{"Name":"Film","Type":"Movie","ProductionYear":1999},"PlayState":{"PlayMethod":"DirectPlay"}}]"""
        val p = ServerAdminParser.sessions(text).single().playing!!
        assertEquals("1999", p.subtitle)
        assertEquals(PlayMethod.Direct, p.method)
    }

    @Test fun parsesTasks() {
        val text = """[
            {"Id":"t1","Name":"Scan","Category":"Library","State":"Running","CurrentProgressPercentage":42.5,"LastExecutionResult":{"Status":"Completed","EndTimeUtc":"2026-10-10T01:00:00Z"}},
            {"Id":"t2","Name":"Clean","State":"Idle","IsHidden":true,"LastExecutionResult":{"Status":"Failed","ErrorMessage":"disk full"}},
            {"Id":"t3","Name":"New","State":"Idle"}
        ]"""
        val tasks = ServerAdminParser.tasks(text)
        assertTrue(tasks[0].running)
        assertEquals(42.5, tasks[0].progress!!, 0.001)
        assertEquals("Failed", tasks[1].lastStatus)
        assertEquals("disk full", tasks[1].lastError)
        assertTrue(tasks[1].hidden)
        assertNull(tasks[2].lastStatus)
    }

    @Test fun parsesActivityAndClassifiesIt() {
        val text = """{"Items":[
            {"Id":3,"Name":"Sam failed to sign in","Overview":"Chrome 10.0.0.9\n\n\n\nTime\n\nSunday","Type":"user.authenticationfailed","Date":"2026-10-10T10:00:00Z","Severity":"Warn"},
            {"Id":2,"Name":"Sam signed in","Overview":"Chrome 10.0.0.5","Type":"user.authenticated","Date":"2026-10-10T09:00:00Z","Severity":"Info"},
            {"Id":1,"Name":"Sam started playback","Type":"playback.start","Severity":"Info"},
            {"Id":0,"Name":"Something broke","Type":"system.error","Severity":"Error"}
        ]}"""
        val entries = ServerAdminParser.activity(text)
        assertEquals(4, entries.size)
        assertEquals("Chrome 10.0.0.9", entries[0].detail)
        assertNull(ServerAdminParser.activity("""{"Items":[{"Id":9,"Name":"x","Overview":"


Time

Sunday","Type":"user.created"}]}""").single().detail)
        assertEquals(ActivityKind.SignInFailed, entries[0].kind)
        assertTrue(entries[0].isProblem)
        assertEquals(ActivityKind.SignIn, entries[1].kind)
        assertFalse(entries[1].isProblem)
        assertEquals(ActivityKind.Playback, entries[2].kind)
        assertTrue(entries[3].isProblem)
    }

    @Test fun parsesDevicesNewestFirst() {
        val text = """{"Items":[
            {"Id":"1","ReportedDeviceId":"a","Name":"Old phone","AppName":"App","AppVersion":"1","LastUserName":"Sam","DateLastActivity":"2026-10-01T00:00:00Z","IpAddress":"1.1.1.1"},
            {"Id":"2","ReportedDeviceId":"b","Name":"New phone","AppName":"App","AppVersion":"2","LastUserName":"Kim","DateLastActivity":"2026-10-09T00:00:00Z"}
        ]}"""
        val devices = ServerAdminParser.devices(text)
        assertEquals(listOf("New phone", "Old phone"), devices.map { it.name })
        assertEquals("b", devices[0].reportedId)
        assertEquals("", devices[0].ip)
    }

    @Test fun parsesLibrariesAndPicksWhatToCount() {
        val libs = ServerAdminParser.libraries("""[{"Name":"Movies","ItemId":"5","CollectionType":"movies","Locations":["C:\\Movies"]},{"Name":"NoId"}]""")
        assertEquals(1, libs.size)
        assertEquals("Movies", libs[0].name)
        assertEquals(listOf("C:\\Movies"), libs[0].paths)
        assertEquals("Movie" to "movies", ServerAdminParser.countFilter("movies"))
        assertEquals("Series" to "shows", ServerAdminParser.countFilter("tvshows"))
        assertEquals(null to "items", ServerAdminParser.countFilter("homevideos"))
    }

    @Test fun garbageGivesEmptyListsNotCrashes() {
        assertTrue(ServerAdminParser.sessions("not json").isEmpty())
        assertTrue(ServerAdminParser.activity("[]").isEmpty())
        assertTrue(ServerAdminParser.devices("{}").isEmpty())
        assertTrue(ServerAdminParser.libraries("{}").isEmpty())
    }

    @Test fun anEpisodeShowsItsShowsPosterAndAMovieItsOwn() {
        val text = """[{"Id":"a","NowPlayingItem":{"Id":"77","Type":"Episode","SeriesId":"500","SeriesName":"A Show"}},
            {"Id":"b","NowPlayingItem":{"Id":"88","Type":"Movie"}},
            {"Id":"c","NowPlayingItem":{"Id":"99","Type":"Episode"}}]"""
        val posters = ServerAdminParser.sessions(text).map { it.playing!!.posterId }
        assertEquals(listOf("500", "88", "99"), posters)
    }

    @Test fun sessionsComeInAFixedOrderWhateverOrderEmbySends() {
        val a = """{"Id":"s1","UserName":"Zoe","DeviceName":"TV"}"""
        val b = """{"Id":"s2","UserName":"adam","DeviceName":"Phone"}"""
        val c = """{"Id":"s3","UserName":"adam","DeviceName":"Laptop"}"""
        val one = ServerAdminParser.sessions("[$a,$b,$c]").map { it.id }
        val two = ServerAdminParser.sessions("[$c,$a,$b]").map { it.id }
        assertEquals(listOf("s3", "s2", "s1"), one) // adam/Laptop, adam/Phone, Zoe (case ignored)
        assertEquals(one, two)
    }

    @Test fun readsTheShowAnEpisodeBelongsTo() {
        assertEquals("500", ServerAdminParser.seriesId("""{"Items":[{"Id":"77","SeriesId":"500"}],"TotalRecordCount":1}"""))
        assertEquals(null, ServerAdminParser.seriesId("""{"Items":[{"Id":"88"}]}"""))
        assertEquals(null, ServerAdminParser.seriesId("""{"Items":[]}"""))
        assertEquals(null, ServerAdminParser.seriesId("not json"))
    }
}
