package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.HallPassParser
import com.feerlessleadr.embidextrous.hallpass.HallPassState
import com.feerlessleadr.embidextrous.hallpass.HpFilter
import com.feerlessleadr.embidextrous.hallpass.HpMode
import com.feerlessleadr.embidextrous.hallpass.HpReason
import com.feerlessleadr.embidextrous.hallpass.HpScope
import com.feerlessleadr.embidextrous.hallpass.describeView
import com.feerlessleadr.embidextrous.hallpass.swipeOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HallPassSwipeTest {
    @Test
    fun aDragIsADecisionOnlyPastTheThreshold() {
        val width = 1000f
        assertEquals("clean", swipeOutcome(400f, width))
        assertEquals("explicit", swipeOutcome(-400f, width))
        assertEquals("clean", swipeOutcome(300f, width)) // exactly the threshold counts
        assertNull(swipeOutcome(299f, width))
        assertNull(swipeOutcome(-299f, width))
        assertNull(swipeOutcome(0f, width))
        assertNull(swipeOutcome(900f, 0f)) // no width yet: never decide
    }

    @Test
    fun theViewIsDescribedForTheConfirmation() {
        val base = HallPassState()
        assertEquals("Needs review", describeView(base))
        assertEquals("Needs review · No evidence", describeView(base.copy(reason = HpReason.NoEvidence)))
        assertEquals("Needs review · search \"blink\"", describeView(base.copy(search = " blink ")))
        assertEquals("Clean · Abe", describeView(base.copy(filter = HpFilter.Clean, scope = HpScope("Abe", null))))
        assertEquals("Needs review · Alpha", describeView(base.copy(scope = HpScope("Abe", "Alpha"))))
        // a reason only counts with the Needs review filter
        assertEquals("Explicit", describeView(base.copy(filter = HpFilter.Explicit, reason = HpReason.Suspected)))
    }

    @Test
    fun groupedMeansAlbumsOrArtistsWithNothingOpened() {
        assertFalse(HallPassState().grouped)
        assertTrue(HallPassState(mode = HpMode.Albums).grouped)
        assertTrue(HallPassState(mode = HpMode.Artists).grouped)
        assertFalse(HallPassState(mode = HpMode.Artists, scope = HpScope("Abe", null)).grouped)
    }

    @Test
    fun playsAndArtistsAreRead() {
        val (_, rows) = HallPassParser.review("""{"Total":1,"Items":[{"Id":5,"Name":"Song","Plays":12}]}""")
        assertEquals(12, rows[0].plays)
        assertEquals(0, HallPassParser.review("""{"Total":1,"Items":[{"Id":5,"Name":"Song"}]}""").second[0].plays) // older plugin
        val (total, artists) = HallPassParser.artists(
            """{"Total":1,"Items":[{"Artist":"Abe","Matching":3,"Total":4,"Albums":2,"ByDecision":{"Review":3},"Plays":40}]}""",
        )
        assertEquals(1, total)
        assertEquals("Abe", artists[0].artist)
        assertEquals(2, artists[0].albums)
        assertEquals(40, artists[0].plays)
        val (_, albums) = HallPassParser.albums("""{"Total":1,"Items":[{"Artist":"Abe","Album":"A","Matching":1,"Total":1,"ByDecision":{},"Plays":9}]}""")
        assertEquals(9, albums[0].plays)
    }
}
