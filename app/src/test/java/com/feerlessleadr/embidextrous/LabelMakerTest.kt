package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.LabelMaker
import com.feerlessleadr.embidextrous.data.LabelMakerParser
import com.feerlessleadr.embidextrous.data.LmGate
import com.feerlessleadr.embidextrous.data.LmDuplicateChoice
import com.feerlessleadr.embidextrous.labelmaker.LmFilter
import com.feerlessleadr.embidextrous.labelmaker.lmAgo
import com.feerlessleadr.embidextrous.labelmaker.lmHint
import com.feerlessleadr.embidextrous.push.EmbyMessages
import com.feerlessleadr.embidextrous.push.EmbyTopic
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMakerTest {
    private val statusJson = """
        {"ApiLevel":1,"Version":42,"ServerTimeUtc":"2026-11-03T12:00:00Z","Paused":false,"DryRun":true,"LastPassUtc":"2026-11-03T11:58:00Z","MatchingVersion":3,
         "Counts":{"Waiting":1,"NeedsReview":4,"NoMatch":2,"Ready":3,"Applying":0,"Done":50,"Failed":1,"Duplicate":0,"Skipped":0},
         "Lanes":[{"LaneId":"main","LaneName":"Main","Counts":{"NeedsReview":4},"Problems":[{"Kind":"InboxMissing","Message":"The inbox folder is missing.","Blocking":true}]},
                  {"LaneId":"kids","LaneName":"Kids","Counts":{},"Problems":[]}],
         "Service":{"MusicBrainz":"Trouble","SinceUtc":"2026-11-03T11:00:00Z"},"LatestSeq":9}
    """.trimIndent()

    @Test fun readsTheStatusTheRestRouteAndTheSocketBothSend() {
        val s = LabelMakerParser.status(statusJson)
        assertEquals(42L, s.version)
        assertTrue(s.dryRun)
        assertFalse(s.paused)
        assertEquals(4, s.count("NeedsReview"))
        assertEquals(0, s.count("Nonsense"))
        assertEquals(listOf("Main", "Kids"), s.lanes.map { it.name })
        assertEquals(true, s.lanes[0].problems[0].blocking)
        assertTrue(s.musicBrainzTrouble)
        assertNull(s.error)
        // the socket's Data object is the same body
        val pushed = EmbyMessages.parse("""{"MessageType":"LabelMakerStatus","Data":$statusJson}""")!!
        assertEquals(s, LabelMakerParser.status(pushed.data as JsonObject))
    }

    @Test fun aRefusedSubscriptionIsAnError() {
        val e = EmbyMessages.parse("""{"MessageType":"LabelMakerStatus","Data":{"Error":"Administrator login required."}}""")!!
        assertEquals("Administrator login required.", LabelMakerParser.status(e.data as JsonObject).error)
    }

    @Test fun aMissingFieldBecomesADefaultNotACrash() {
        val s = LabelMakerParser.status("{}")
        assertEquals(0L, s.version)
        assertTrue(s.lanes.isEmpty())
        assertFalse(s.musicBrainzTrouble)
        val a = LabelMakerParser.albums("""{"Total":1,"Items":[{"Id":"abc123","State":"NeedsReview"}]}""")
        assertEquals(1, a.first)
        assertEquals("", a.second[0].artist)
        assertEquals(0.0, a.second[0].score, 0.0)
    }

    @Test fun theStatusPushStartMessageCarriesOnlyTheTokenAfterTheInterval() {
        assertEquals("""{"MessageType":"LabelMakerStatusStart","Data":"0,30000,tok"}""", EmbyMessages.start(EmbyTopic.LabelMakerStatus, token = "tok"))
        assertEquals("""{"MessageType":"LabelMakerStatusStop"}""", EmbyMessages.stop(EmbyTopic.LabelMakerStatus))
        // the alert subscription still sends its event number: empty the first time
        assertEquals("""{"MessageType":"LabelMakerEventsStart","Data":"0,30000,,tok"}""", EmbyMessages.start(EmbyTopic.LabelMakerEvents, token = "tok"))
        // the status topic is not an alert subscription, so the alert service never subscribes to it
        assertNull(EmbyTopic.LabelMakerStatus.eventsPlugin)
    }

    @Test fun anAlbumPageAndItsDetailAreRead() {
        val page = LabelMakerParser.albums(
            """{"Total":31,"Start":0,"Items":[{"Id":"a1","LaneId":"main","LaneName":"Main","Artist":"Green Day","Album":"Insomniac","Folder":"Green Day/Insomniac","Kind":"Album",
               "State":"Ready","Reason":"Matched.","Files":14,"Held":false,"Discarded":false,"Score":0.9731,"BestReleaseId":"r1","BestTitle":"Insomniac","BestArtist":"Green Day","Destination":"Green Day/Insomniac"}]}""",
        )
        assertEquals(31, page.first)
        val a = page.second.single()
        assertEquals("a1", a.id)
        assertEquals(14, a.files)
        assertEquals("97%", LabelMakerParser.percent(a.score))
        val d = LabelMakerParser.detail(
            """{"Id":"a1","State":"NeedsReview","Candidates":[{"ReleaseId":"r1","Title":"Insomniac","Artist":"Green Day","Country":"US","Date":"1995-10-10","Score":0.81,"WeakFiles":2}],
               "FileList":[{"File":"01.mp3","Title":"Armatage Shanks","Artist":"Green Day","Album":"Insomniac","Track":1,"Disc":1,"Seconds":135},{"File":"bad.mp3","Error":"Could not read this file."}],
               "Notes":["Searched by album."],"Outcome":"NeedsReview"}""",
        )
        assertEquals(1, d.candidates.size)
        assertEquals(2, d.candidates[0].weakFiles)
        assertEquals(2, d.files.size)
        assertEquals("Could not read this file.", d.files[1].error)
        assertEquals(listOf("Searched by album."), d.notes)
    }

    @Test fun aPreviewAndAnActionAnswerAreRead() {
        val p = LabelMakerParser.preview(
            """{"Success":true,"Message":"","ReleaseId":"r1","Title":"Insomniac","Artist":"Green Day","Date":"1995","Country":"US","Score":0.95,
               "Files":[{"Source":"01.mp3","Destination":"Green Day/Insomniac/01 Armatage Shanks.mp3","Disc":1,"Position":1,"Title":"Armatage Shanks","Score":0.99,"Weak":false,"Existing":true,"ExistingBytes":10,"NewBytes":12}],
               "Problems":["One file is too short."],"Notes":["Using edition US."],"CanApply":false,"IsDuplicate":true,"DuplicateCount":1,"Destination":"Green Day/Insomniac"}""",
        )
        assertTrue(p.isDuplicate)
        assertFalse(p.canApply)
        assertEquals(1, p.duplicateCount)
        assertTrue(p.files[0].existing)
        assertEquals(listOf("One file is too short."), p.problems)
        val r = LabelMakerParser.action("""{"Success":true,"Message":"Filed 14 files.","State":"Done"}""")
        assertTrue(r.success)
        assertEquals("Done", r.state)
        val s = LabelMakerParser.search("""{"Success":false,"Message":"Nothing found.","Candidates":[]}""")
        assertFalse(s.success)
        assertTrue(s.candidates.isEmpty())
    }

    @Test fun duplicateChoicesUseTheWordsThePluginExpects() {
        assertEquals(listOf("", "KeepBoth", "Replace", "SkipExisting"), LmDuplicateChoice.entries.map { it.wire })
    }

    @Test fun thePluginIsGatedByItsVersion() {
        fun gate(version: String, level: Int = 1) = LabelMakerParser.gate(LabelMakerParser.info("""{"PluginVersion":"$version","ApiLevel":$level,"Features":["albums","statuspush"]}"""))
        assertTrue(gate("0.1.11.0") is LmGate.Ready)
        assertTrue(gate("0.1.8.0") is LmGate.Ready)
        assertTrue(gate("0.1.7.0") is LmGate.TooOld)
        assertTrue(gate("0.2.0.0") is LmGate.Ready)
        assertTrue(gate("garbage") is LmGate.TooOld)
        assertTrue(gate("0.1.11.0", level = 0) is LmGate.TooOld)
        assertEquals(setOf("albums", "statuspush"), LabelMakerParser.info("""{"PluginVersion":"0.1.11.0","ApiLevel":1,"Features":["albums","statuspush"]}""").features)
    }

    @Test fun theHintSaysWhatToDoNext() {
        fun status(counts: String, paused: Boolean = false, dry: Boolean = true, problem: Boolean = false) = LabelMakerParser.status(
            """{"Version":1,"Paused":$paused,"DryRun":$dry,"Counts":$counts,"Lanes":[{"LaneId":"m","LaneName":"M","Counts":{},"Problems":[${if (problem) """{"Kind":"x","Message":"m","Blocking":true}""" else ""}]}]}""",
        )
        assertEquals("A lane has a problem and is stopped. See below.", lmHint(status("""{"NeedsReview":2}""", problem = true)))
        assertEquals("Label Maker is paused. Resume it to look at the inbox again.", lmHint(status("""{"NeedsReview":2}""", paused = true)))
        assertEquals("1 album needs your decision.", lmHint(status("""{"NeedsReview":1}""")))
        assertEquals("4 albums need your decision.", lmHint(status("""{"NeedsReview":4,"Ready":2}""")))
        assertEquals("2 albums are Ready. Dry run is on, so nothing is filed yet.", lmHint(status("""{"Ready":2}""")))
        assertEquals("1 album is Ready and will be filed on the next look.", lmHint(status("""{"Ready":1}""", dry = false)))
        assertEquals("Nothing needs you right now.", lmHint(status("""{"Done":9}""")))
    }

    @Test fun theLastLookIsToldInPlainWords() {
        val now = java.time.Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()
        assertEquals("", lmAgo("", now))
        assertEquals("", lmAgo("not a time", now))
        assertEquals("just now", lmAgo("2026-11-03T11:59:00Z", now))
        assertEquals("5 minutes ago", lmAgo("2026-11-03T11:55:00Z", now))
        assertEquals("1 hour ago", lmAgo("2026-11-03T10:50:00Z", now))
        assertEquals("3 hours ago", lmAgo("2026-11-03T08:30:00Z", now))
        assertEquals("2 days ago", lmAgo("2026-11-01T11:00:00Z", now))
        assertEquals("just now", lmAgo("2026-11-03T12:30:00Z", now)) // a clock a little ahead is not a negative time
    }

    @Test fun theFiltersCountWhatTheStatusCounts() {
        val s = LabelMakerParser.status(statusJson)
        assertEquals(4, LmFilter.Review.count(s))
        assertEquals(3, LmFilter.Ready.count(s))
        assertEquals(1, LmFilter.Waiting.count(s)) // Waiting and Applying together
        assertEquals(61, LmFilter.All.count(s))
        assertNull(LmFilter.Review.count(null))
    }

    @Test fun splitIsOnlyOfferedForAFolderThatHoldsSeveralAlbums() {
        fun album(reason: String, kind: String = "Album") = LabelMakerParser.album(
            kotlinx.serialization.json.buildJsonObject {
                put("Id", kotlinx.serialization.json.JsonPrimitive("a")); put("Reason", kotlinx.serialization.json.JsonPrimitive(reason)); put("Kind", kotlinx.serialization.json.JsonPrimitive(kind))
            },
        )
        assertTrue(LabelMaker.canSplit(album("This folder holds 3 different albums.")))
        assertFalse(LabelMaker.canSplit(album("This folder holds 3 different albums.", kind = "Single")))
        assertFalse(LabelMaker.canSplit(album("No edition fits.")))
        assertTrue(LabelMaker.isBusy("Applying"))
        assertFalse(LabelMaker.isBusy("Done"))
    }
}
