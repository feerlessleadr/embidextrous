package com.feerlessleadr.embidextrous

import com.feerlessleadr.embidextrous.push.EmbyMessages
import com.feerlessleadr.embidextrous.push.EmbyTopic
import com.feerlessleadr.embidextrous.push.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushProtocolTest {
    @Test fun topicsAreSwitchedOnAndOffWithTheRightMessages() {
        assertEquals("""{"MessageType":"SessionsStart","Data":"0,1500"}""", EmbyMessages.start(EmbyTopic.Sessions))
        assertEquals("""{"MessageType":"SessionsStop"}""", EmbyMessages.stop(EmbyTopic.Sessions))
        assertEquals("""{"MessageType":"ScheduledTasksInfoStart","Data":"0,1000"}""", EmbyMessages.start(EmbyTopic.ScheduledTasks))
        assertEquals("""{"MessageType":"ActivityLogEntryStart","Data":"0,1000"}""", EmbyMessages.start(EmbyTopic.Activity))
        // the events topic is no subscription: nothing to send
        assertNull(EmbyMessages.start(EmbyTopic.Events))
        assertNull(EmbyMessages.stop(EmbyTopic.Events))
    }

    @Test fun parsesAPushedMessage() {
        val e = EmbyMessages.parse("""{"MessageType":"Sessions","Data":[{"Id":"s1"}],"ServerId":"x"}""")!!
        assertEquals("Sessions", e.type)
        assertTrue(e.data.toString().contains("s1"))
        // a message with no data is still a message
        assertNull(EmbyMessages.parse("""{"MessageType":"RestartRequired"}""")!!.data)
    }

    @Test fun anythingElseIsIgnored() {
        assertNull(EmbyMessages.parse("not json"))
        assertNull(EmbyMessages.parse("[]"))
        assertNull(EmbyMessages.parse("""{"Data":1}"""))
        assertNull(EmbyMessages.parse("""{"MessageType":""}"""))
        assertNull(EmbyMessages.parse("x".repeat(EmbyMessages.MAX_FRAME_CHARS + 1)))
    }

    @Test fun theServerAsksForKeepAlives() {
        assertEquals(60, EmbyMessages.keepAliveSeconds(EmbyMessages.parse("""{"MessageType":"ForceKeepAlive","Data":60}""")!!))
        assertNull(EmbyMessages.keepAliveSeconds(EmbyMessages.parse("""{"MessageType":"Sessions","Data":[]}""")!!))
    }

    @Test fun theWebsocketAddressKeepsTheSchemeBaseAndNeverHoldsTheToken() {
        assertEquals("wss://emby.example.com/embywebsocket?deviceId=abc", EmbyMessages.url("https://emby.example.com/", "abc"))
        assertEquals("ws://10.0.2.2:8096/embywebsocket?deviceId=a+b", EmbyMessages.url("http://10.0.2.2:8096", "a b"))
        assertEquals("wss://host/emby/embywebsocket?deviceId=d", EmbyMessages.url("https://host/emby", "d"))
        assertTrue(!EmbyMessages.url("https://h", "d").contains("token", ignoreCase = true))
    }

    @Test fun readsServerSentEventsLikeTracearrSendsThem() {
        val parser = SseParser()
        val events = ArrayList<com.feerlessleadr.embidextrous.push.SseEvent>()
        val stream = listOf(
            "retry: 5000", "",
            "event: ready", "data: {\"type\":\"ready\",\"at\":\"2026-10-12T00:00:00Z\",\"data\":{}}", "",
            ": ping", "",
            "event: stream.started", "data: {\"type\":\"stream.started\",", "data: \"data\":{}}", "",
            "data: just data", "",
        )
        for (line in stream) parser.feed(line)?.let { events.add(it) }
        assertEquals(5000L, parser.retryMs)
        assertEquals(listOf("ready", "stream.started", "message"), events.map { it.name })
        assertTrue(events[0].data.contains("\"ready\""))
        assertEquals("{\"type\":\"stream.started\",\n\"data\":{}}", events[1].data) // data lines are joined with a newline
        assertEquals("just data", events[2].data)
    }

    @Test fun commentsAndOddRetryValuesDoNothing() {
        val parser = SseParser()
        assertNull(parser.feed(": ping"))
        assertNull(parser.feed("")) // a blank line with nothing before it ends nothing
        parser.feed("retry: soon")
        parser.feed("retry: 99999999")
        assertNull(parser.retryMs)
        parser.feed("retry: 1500")
        assertEquals(1500L, parser.retryMs)
        assertNotNull(SseParser().apply { feed("event: x") }.feed(""))
    }
}
