package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.watching.LiveMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMessagesTest {
    @Test
    fun aPushedStatusCarriesTheReasonsForTimeChanges() {
        val json = """{"MessageType":"HallMonitorStatus","Data":{"Version":5,"ServerTimeUtc":"2026-11-03T12:00:00Z","Users":[
            {"UserId":"u1","Username":"Harper","ApiLevel":7,"CreditSecondsDaily":900,
             "RecentTimeChanges":[{"Id":638000000000000001,"AtUtc":"2026-11-03T12:00:00Z","Minutes":15,"Kind":"Added","Source":"homeassistant","Reason":"Feed Chloe"}]}]}}"""
        val message = LiveMessages.parse(json) as LiveMessages.Message.Status
        val change = message.users.single().recentTimeChanges!!.single()
        assertEquals(15, change.minutes)
        assertEquals("homeassistant", change.source)
        assertEquals("Feed Chloe", change.reason)
        assertTrue(change.id > 0)
    }

    @Test
    fun aStatusFromAnOlderPluginHasNoChangeList() {
        val json = """{"MessageType":"HallMonitorStatus","Data":{"Users":[{"UserId":"u1","Username":"Harper","ApiLevel":6}]}}"""
        val message = LiveMessages.parse(json) as LiveMessages.Message.Status
        assertEquals(null, message.users.single().recentTimeChanges)
    }
}
