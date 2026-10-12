package com.feerlessleadr.embidextrous.push

import com.feerlessleadr.embidextrous.data.AppJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * What the app asks Emby to push over its websocket. Each topic is switched on with a "...Start" message carrying "initial delay,interval" in
 * milliseconds and answered with messages of [message] until the matching "...Stop". [Events] is no subscription at all: it keeps the connection open for
 * what the server and its plugins send on their own (the plugin manager's run events, for example).
 */
enum class EmbyTopic(val message: String?, val startMessage: String?, val stopMessage: String?, val interval: String?) {
    Sessions("Sessions", "SessionsStart", "SessionsStop", "0,1500"),
    ScheduledTasks("ScheduledTasksInfo", "ScheduledTasksInfoStart", "ScheduledTasksInfoStop", "0,1000"),
    Activity("ActivityLogEntry", "ActivityLogEntryStart", "ActivityLogEntryStop", "0,1000"),
    Events(null, null, null, null),
}

/** One message from Emby: its type and its data (null when there was none). */
data class PushEvent(val type: String, val data: JsonElement?)

/** Reading and writing the messages of Emby's websocket. Pure functions, unit tested. */
object EmbyMessages {
    /** Text frames larger than this are ignored: nothing the app subscribes to comes anywhere near it. */
    const val MAX_FRAME_CHARS = 4_000_000

    fun start(topic: EmbyTopic): String? = topic.startMessage?.let { """{"MessageType":"$it","Data":"${topic.interval}"}""" }

    fun stop(topic: EmbyTopic): String? = topic.stopMessage?.let { """{"MessageType":"$it"}""" }

    fun keepAlive(): String = """{"MessageType":"KeepAlive"}"""

    /** A frame as a [PushEvent], or null when it is not a JSON object with a message type (or is too big to be worth reading). */
    fun parse(text: String): PushEvent? {
        if (text.length > MAX_FRAME_CHARS) return null
        val root = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val type = (root["MessageType"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        return PushEvent(type, root["Data"])
    }

    /** Seconds the server asks the client to keep the connection alive within (its ForceKeepAlive message), or null for any other message. */
    fun keepAliveSeconds(event: PushEvent): Int? =
        if (event.type == "ForceKeepAlive") (event.data as? JsonPrimitive)?.intOrNull ?: 0 else null

    /** http(s)://host[:port][/base] as the Emby websocket address. The token goes in a header, never in the address. */
    fun url(serverUrl: String, deviceId: String): String =
        serverUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/embywebsocket?deviceId=" + java.net.URLEncoder.encode(deviceId, "UTF-8")
}

/** One server-sent event: its name (the "event:" line) and the text of its "data:" lines. */
data class SseEvent(val name: String, val data: String)

/**
 * Reads a server-sent-event stream line by line ([feed]): "event:" names the event, "data:" lines are joined with newlines, a blank line ends it,
 * lines starting with ":" are comments (heartbeats) and "retry:" tells how long to wait before reconnecting. Pure, unit tested.
 */
class SseParser {
    private var name = ""
    private val data = StringBuilder()
    private var hasData = false

    /** The reconnect delay the server asked for, in milliseconds, once it has said so. */
    var retryMs: Long? = null
        private set

    /** Feeds one line (without its line break); returns the event this line completes, if any. */
    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) {
            val event = if (hasData || name.isNotEmpty()) SseEvent(name.ifEmpty { "message" }, data.toString()) else null
            name = ""
            data.setLength(0)
            hasData = false
            return event
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> name = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "retry" -> value.trim().toLongOrNull()?.takeIf { it in 0..600_000 }?.let { retryMs = it }
        }
        return null
    }
}
