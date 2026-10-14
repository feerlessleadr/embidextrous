package com.feerlessleadr.embidextrous.push

import com.feerlessleadr.embidextrous.data.AppJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * What the app asks Emby to push over its websocket. Each topic is switched on with a "...Start" message carrying "initial delay,interval" in
 * milliseconds and answered with messages of [message] until the matching "...Stop". [Events] is no subscription at all: it keeps the connection open for
 * what the server and its plugins send on their own (the plugin manager's run events, for example).
 */
enum class EmbyTopic(
    val message: String?,
    val startMessage: String?,
    val stopMessage: String?,
    val interval: String?,
    /** For a plugin's event subscription: the plugin name, whose start message also carries the last event number the app has seen (docs/push-contract.md). */
    val eventsPlugin: String? = null,
) {
    Sessions("Sessions", "SessionsStart", "SessionsStop", "0,1500"),
    ScheduledTasks("ScheduledTasksInfo", "ScheduledTasksInfoStart", "ScheduledTasksInfoStop", "0,1000"),
    Activity("ActivityLogEntry", "ActivityLogEntryStart", "ActivityLogEntryStop", "0,1000"),
    Events(null, null, null, null),

    // Plugins that send alerts: the app subscribes and the plugin pushes to this one socket (it works with the token in the header, unlike "send to admin sessions").
    LabelMakerEvents("LabelMakerEvents", "LabelMakerEventsStart", "LabelMakerEventsStop", "0,30000", "LabelMaker"),
    HallPassEvents("HallPassEvents", "HallPassEventsStart", "HallPassEventsStop", "0,30000", "HallPass"),
    SmartListsEvents("SmartListsEvents", "SmartListsEventsStart", "SmartListsEventsStop", "0,30000", "SmartLists"),
    PluginManagerEvents("GitHubRepoPluginInstallEvents", "GitHubRepoPluginInstallEventsStart", "GitHubRepoPluginInstallEventsStop", "0,30000", "GitHubRepoPluginInstall"),
}

/** One message from Emby: its type and its data (null when there was none). */
data class PushEvent(val type: String, val data: JsonElement?)

/** Reading and writing the messages of Emby's websocket. Pure functions, unit tested. */
object EmbyMessages {
    /** Text frames larger than this are ignored: nothing the app subscribes to comes anywhere near it. */
    const val MAX_FRAME_CHARS = 4_000_000

    /**
     * The start message of a topic. A plugin's event subscription carries, after the interval, the highest event number the app has seen ([afterSeq], empty for none)
     * and the access token ([token]): the plugin checks it itself, because Emby does not tell a plugin who a header-authenticated socket belongs to (docs/push-contract.md).
     * The token travels in the encrypted message, never in the address.
     */
    fun start(topic: EmbyTopic, afterSeq: Long? = null, token: String? = null): String? = topic.startMessage?.let {
        // Nothing seen yet is an EMPTY value (the plugin then only reports where its log stands); a 0 would replay the whole log.
        val data = if (topic.eventsPlugin != null && token != null) "${topic.interval},${afterSeq?.takeIf { it > 0 } ?: ""},$token" else topic.interval
        buildJsonObject {
            put("MessageType", it)
            put("Data", data)
        }.toString()
    }

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
