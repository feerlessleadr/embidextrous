package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.notifications.HallMonitorEngine
import com.feerlessleadr.embidextrous.notifications.NotifCatalog
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.watching.Link
import com.feerlessleadr.embidextrous.watching.LiveMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

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

    @Test
    fun aRealPushFromPlugin25IsParsedAndExplained() {
        // Captured from a live Hall Monitor 2.5.0.0 on the test server after seven add/take-away calls.
        val json = javaClass.classLoader!!.getResource("hall-monitor-2.5-real-push.json")!!.readText()
        val status = (LiveMessages.parse(json) as LiveMessages.Message.Status).users.single()
        assertEquals(7, status.recentTimeChanges!!.size)
        val rules = listOf(NotifRule("r", NotifCatalog.Ids.HM_EXTRA_TIME, true, emptyMap(), emptyList()))
        val engine = HallMonitorEngine(ZoneOffset.UTC)
        val now = System.currentTimeMillis()
        val quiet = status.copy(recentTimeChanges = emptyList())
        engine.update(rules, listOf(UserCard(quiet, now)), Link.LIVE, now, emptyMap())
        val d = engine.update(rules, listOf(UserCard(status, now)), Link.LIVE, now, emptyMap()).deliveries.single()
        assertEquals("we-reason's time changed", d.title)
        assertTrue(d.text, d.text.contains("15 min added because the Feed Chloe chore was marked completed."))
        assertTrue(d.text, d.text.contains("30 min added manually by a parent."))
        assertTrue(d.text, d.text.contains("10 min taken away: Traded for play time."))
        assertTrue(d.text, d.text.contains("2 min added manually by a parent."))
    }
}