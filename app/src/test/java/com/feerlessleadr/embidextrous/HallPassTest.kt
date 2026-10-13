package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.HallPassParser
import com.feerlessleadr.embidextrous.data.HpGate
import com.feerlessleadr.embidextrous.data.HpInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HallPassTest {
    @Test
    fun statusIsRead() {
        val s = HallPassParser.status(
            """{"Job":{"State":"Running","Total":100,"Processed":40,"Message":"x"},"Write":{"State":"Idle"},
               "Stats":{"Total":61,"ByDecision":{"Explicit":6,"Review":17},"ByReason":{},"Overrides":2},"DryRun":true,"Preset":"Balanced","Version":"1.1.0.0"}""",
        )
        assertEquals(61, s.total)
        assertEquals(6, s.count("Explicit"))
        assertEquals(0, s.count("Clean"))
        assertEquals(2, s.overrides)
        assertTrue(s.dryRun)
        assertTrue(s.scan.isRunning)
        assertEquals(40, s.scan.processed)
        assertFalse(s.write.isRunning)
    }

    @Test
    fun reviewRowsAreRead() {
        val (total, rows) = HallPassParser.review(
            """{"Total":120,"Items":[{"Id":42,"Name":"Song","Artist":"A","Album":"B","Decision":"Review","Confidence":0.5,"ReviewReason":"Suspected","Reason":"r",
               "Override":"None","Skipped":false,"Evidence":["lyrics: Explicit 0.50 (suspect)"]},{"Id":43,"Name":"Two","Override":"Explicit"}]}""",
        )
        assertEquals(120, total)
        assertEquals(2, rows.size)
        assertEquals(42L, rows[0].id)
        assertFalse(rows[0].manual)
        assertEquals(1, rows[0].evidence.size)
        assertTrue(rows[1].manual)
        assertEquals("", rows[1].artist)
    }

    @Test
    fun albumsAreRead() {
        val (total, albums) = HallPassParser.albums("""{"Total":1,"Items":[{"Artist":"Abe","Album":"","Matching":2,"Total":3,"ByDecision":{"Explicit":1,"Review":1}}]}""")
        assertEquals(1, total)
        assertEquals("", albums[0].album)
        assertEquals(2, albums[0].matching)
        assertEquals(1, albums[0].byDecision["Explicit"])
    }

    @Test
    fun decideAndPreviewAreRead() {
        val d = HallPassParser.decide("""{"Changed":5,"Matched":7,"Truncated":true}""")
        assertEquals(5, d.changed)
        assertTrue(d.truncated)
        val p = HallPassParser.preview("""{"Total":61,"WouldChange":64,"RollUps":3,"Unchanged":0,"DryRun":false,"ByTag":{"explicit":7,"clean":2}}""")
        assertEquals(64, p.wouldChange)
        assertEquals(3, p.rollUps)
        assertEquals(7, p.byTag["explicit"])
    }

    @Test
    fun theGateNeedsTheNewVersionAndTheApi() {
        assertTrue(HallPassParser.gate(HpInfo("1.2.0.0", 1)) is HpGate.Ready)
        assertTrue(HallPassParser.gate(HpInfo("1.2.3.4", 1)) is HpGate.Ready)
        assertTrue(HallPassParser.gate(HpInfo("1.1.2.0", 1)) is HpGate.TooOld)
        assertTrue(HallPassParser.gate(HpInfo("1.1.9.0", 1)) is HpGate.TooOld)
        assertTrue(HallPassParser.gate(HpInfo("1.2.0.0", 0)) is HpGate.TooOld)
        assertTrue(HallPassParser.gate(HpInfo("weird", 1)) is HpGate.TooOld)
        assertTrue(HallPassParser.atLeast("2.0", "1.9.9.9"))
        assertFalse(HallPassParser.atLeast("1.10.0.0", "1.11.0.0"))
    }

    @Test
    fun garbageDoesNotCrashTheReadersThatAreLenient() {
        val s = HallPassParser.status("{}")
        assertEquals(0, s.total)
        assertFalse(s.scan.isRunning)
    }
}

class HallPassNextStepTest {
    private fun status(total: Int, review: Int, dry: Boolean, scanning: Boolean = false, writing: Boolean = false) = com.feerlessleadr.embidextrous.data.HallPassParser.status(
        """{"Job":{"State":"${if (scanning) "Running" else "Idle"}"},"Write":{"State":"${if (writing) "Running" else "Idle"}"},"Stats":{"Total":$total,"ByDecision":{"Review":$review},"ByReason":{}},"DryRun":$dry}""",
    )

    @Test
    fun theStepFollowsWhatHasHappened() {
        assertEquals(1, com.feerlessleadr.embidextrous.hallpass.nextStep(status(0, 0, true)).number)
        assertEquals(1, com.feerlessleadr.embidextrous.hallpass.nextStep(status(10, 0, true, scanning = true)).number)
        assertEquals(2, com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 517, true)).number)
        assertTrue(com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 517, true)).text.startsWith("517 tracks"))
        assertEquals(3, com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 0, true)).number)
        assertTrue(com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 0, true)).text.contains("Dry run"))
        assertEquals(4, com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 0, false)).number)
        assertEquals(5, com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 0, false, writing = true)).number)
        // with review left but dry run already off the review step still comes first
        assertEquals(2, com.feerlessleadr.embidextrous.hallpass.nextStep(status(517, 20, false)).number)
    }
}
