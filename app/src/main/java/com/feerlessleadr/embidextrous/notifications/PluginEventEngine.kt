package com.feerlessleadr.embidextrous.notifications

import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.TrViolation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/** What a pushed or caught-up event produced: notifications to show and keys of notifications to remove. */
data class EventResult(val deliveries: List<NotifDelivery> = emptyList(), val clears: List<String> = emptyList()) {
    operator fun plus(other: EventResult) = EventResult(deliveries + other.deliveries, clears + other.clears)
}

/** The highest event number seen per service, so a push that arrives again (or a catch-up that overlaps) is shown once. Pure Kotlin. */
class SeqTracker(initial: Map<String, Long> = emptyMap()) {
    private val last = HashMap(initial)

    /** True the first time a number is seen for [service] (and moves the mark); false for a number already seen or older. A missing number always counts. */
    @Synchronized
    fun accept(service: NotifService, seq: Long?): Boolean {
        if (seq == null) return true
        val key = service.name
        if (seq <= (last[key] ?: 0L)) return false
        last[key] = seq
        return true
    }

    @Synchronized
    fun lastSeen(service: NotifService): Long = last[service.name] ?: 0L

    @Synchronized
    fun snapshot(): Map<String, Long> = HashMap(last)
}

/**
 * Turns the events plugins push (Label Maker, Hall Pass, Smart lists, the plugin manager, Emby itself) into notifications for the rules that are on.
 * Each event's `Data` object follows `docs/push-contract.md` (Label Maker's `docs/NOTIFICATIONS.md` for that plugin). Parsing is tolerant: a
 * missing field gives a plainer sentence, never a crash. No Android classes: unit tested.
 */
object PluginEventEngine {
    fun seqOf(data: JsonObject?): Long? = (data?.get("Seq") as? JsonPrimitive)?.longOrNull

    /** The service a pushed message belongs to, or null when it is none of ours. */
    fun serviceOf(type: String): NotifService? = when {
        type.startsWith("LabelMaker.") -> NotifService.LabelMaker
        type.startsWith("HallPass.") -> NotifService.HallPass
        type.startsWith("SmartLists.") -> NotifService.SmartLists
        type.startsWith("GitHubRepoPluginInstall.") -> NotifService.PluginManager
        else -> null
    }

    fun handle(type: String, data: JsonElement?, rules: List<NotifRule>): EventResult {
        val service = serviceOf(type) ?: return EventResult()
        val obj = data as? JsonObject
        return when (service) {
            NotifService.LabelMaker -> labelMaker(type.removePrefix("LabelMaker."), obj, rules)
            NotifService.HallPass -> hallPass(type.removePrefix("HallPass."), obj, rules)
            NotifService.SmartLists -> smartLists(type.removePrefix("SmartLists."), obj, rules)
            NotifService.PluginManager -> pluginManager(type.removePrefix("GitHubRepoPluginInstall."), obj, rules)
            else -> EventResult()
        }
    }

    private fun on(rules: List<NotifRule>, eventId: String) = rules.filter { it.enabled && it.event == eventId }

    private fun make(eventId: String, rule: NotifRule, key: String, title: String, text: String): NotifDelivery {
        val spec = NotifCatalog.spec(eventId)
        return NotifDelivery(
            spec?.service ?: NotifService.Server, rule.id, "$key|${rule.id}", title, text,
            spec?.lockScreenText ?: "Something happened. Unlock to see it.", spec?.opensTab,
        )
    }

    private fun one(eventId: String, rules: List<NotifRule>, key: String, title: String, text: String): EventResult =
        EventResult(on(rules, eventId).map { make(eventId, it, key, title, text) })

    // ---- Label Maker ---------------------------------------------------------------------------------------

    private fun labelMaker(event: String, d: JsonObject?, rules: List<NotifRule>): EventResult = when (event) {
        "NeedsReview" -> {
            val count = d.int("Count") ?: d.int("New") ?: 1
            val albums = d.array("Albums").mapNotNull { (it as? JsonObject)?.let { a -> album(a) } }
            val more = if (count > albums.size && albums.isNotEmpty()) " and ${count - albums.size} more" else ""
            val text = if (albums.isEmpty()) "Open Label Maker to review them." else albums.take(3).joinToString("; ") + more
            EventResult(
                on(rules, NotifCatalog.Ids.LM_NEEDS_REVIEW).filter { count >= it.int("MinCount", 1) }.map {
                    make(NotifCatalog.Ids.LM_NEEDS_REVIEW, it, "lm-review", if (count == 1) "1 album needs review" else "$count albums need review", text)
                },
            )
        }
        "AutoApplied" -> {
            val count = d.int("Count") ?: 0
            val dry = d.str("Mode") == "WouldApply"
            one(
                NotifCatalog.Ids.LM_AUTO_APPLIED, rules, "lm-auto",
                if (dry) "$count album(s) would have been tagged" else "$count album(s) were tagged and filed",
                if (dry) "Label Maker is in dry run mode, so nothing was changed." else "Label Maker did this by itself.",
            )
        }
        "AlbumFailed" -> one(
            NotifCatalog.Ids.LM_ALBUM_FAILED, rules, "lm-failed|${d.str("Id").orEmpty()}",
            "An album failed: ${d.str("Artist").orEmpty()} ${d.str("Album").orEmpty()}".trim(), d.str("Reason") ?: "Open Label Maker to see why.",
        )
        "LaneProblem" -> one(
            NotifCatalog.Ids.LM_LANE_PROBLEM, rules, "lm-lane|${d.str("LaneId").orEmpty()}|${d.str("Kind").orEmpty()}",
            "${d.str("LaneName") ?: "A lane"} has a problem", d.str("Message") ?: "Open Label Maker to see what is wrong.",
        )
        "LaneRecovered" -> EventResult(clears = on(rules, NotifCatalog.Ids.LM_LANE_PROBLEM).map { "lm-lane|${d.str("LaneId").orEmpty()}|${d.str("Kind").orEmpty()}|${it.id}" })
        "ServiceTrouble" -> {
            val waiting = d.int("WaitingCount") ?: 0
            val minutes = minutesSince(d.str("SinceUtc"), d.str("ServerTimeUtc"))
            EventResult(
                on(rules, NotifCatalog.Ids.LM_SERVICE_TROUBLE).filter { minutes == null || minutes >= it.int("Minutes", 15) }.map {
                    make(NotifCatalog.Ids.LM_SERVICE_TROUBLE, it, "lm-service", "Music lookups are stuck", "$waiting album(s) are waiting for MusicBrainz.")
                },
            )
        }
        "ServiceRecovered" -> EventResult(clears = on(rules, NotifCatalog.Ids.LM_SERVICE_TROUBLE).map { "lm-service|${it.id}" })
        else -> EventResult()
    }

    private fun album(a: JsonObject): String? {
        val artist = a.str("Artist").orEmpty()
        val name = a.str("Album").orEmpty()
        return listOf(artist, name).filter { it.isNotEmpty() }.joinToString(": ").ifEmpty { null }
    }

    // ---- Hall Pass -----------------------------------------------------------------------------------------

    private fun hallPass(event: String, d: JsonObject?, rules: List<NotifRule>): EventResult = when (event) {
        "ScanFinished" -> {
            val review = d.int("NeedsReview")
            val scanned = d.int("Scanned") ?: d.int("Total")
            one(
                NotifCatalog.Ids.HP_SCAN_FINISHED, rules, "hp-scan", "A Hall Pass scan finished",
                listOfNotNull(scanned?.let { "$it track(s) checked." }, review?.let { "$it need review." }, d.int("Failed")?.takeIf { it > 0 }?.let { "$it failed." }).joinToString(" ").ifEmpty { "Open Hall Pass to see the result." },
            )
        }
        "ScanFailed" -> one(NotifCatalog.Ids.HP_SCAN_FAILED, rules, "hp-scan", "A Hall Pass scan did not finish", d.str("Message") ?: "Open Hall Pass to see why.")
        "ApplyFinished" -> {
            val kind = if (d.str("Kind") == "Undo") "Undo" else "Apply"
            val text = listOfNotNull(
                d.int("Changed")?.let { "$it changed." }, d.int("Failed")?.takeIf { it > 0 }?.let { "$it failed." }, d.int("Conflicts")?.takeIf { it > 0 }?.let { "$it conflicts." },
            ).joinToString(" ").ifEmpty { "Open Hall Pass to see the result." }
            one(NotifCatalog.Ids.HP_APPLY_FINISHED, rules, "hp-write", "$kind finished", text)
        }
        "ApplyFailed" -> {
            val kind = if (d.str("Kind") == "Undo") "Undo" else "Apply"
            one(NotifCatalog.Ids.HP_APPLY_FAILED, rules, "hp-write", "$kind did not finish", d.str("Message") ?: "Open Hall Pass to see why.")
        }
        "ReviewQueue" -> {
            val count = d.int("Count") ?: 0
            EventResult(
                on(rules, NotifCatalog.Ids.HP_REVIEW_QUEUE).filter { count >= it.int("MinCount", 50) }.map {
                    make(NotifCatalog.Ids.HP_REVIEW_QUEUE, it, "hp-queue", "$count tracks wait for review", "Open Hall Pass to decide them.")
                },
            )
        }
        else -> EventResult()
    }

    // ---- Smart lists ---------------------------------------------------------------------------------------

    private fun smartLists(event: String, d: JsonObject?, rules: List<NotifRule>): EventResult = when (event) {
        "RefreshFailed" -> {
            val lists = d.array("Lists").mapNotNull { it as? JsonObject }
            val first = lists.firstOrNull()
            val count = d.int("Count") ?: lists.size
            val title = if (count > 1) "$count smart lists failed to refresh" else "${first?.str("ListName") ?: "A smart list"} failed to refresh"
            val text = if (count > 1) lists.mapNotNull { it.str("ListName") }.take(3).joinToString(", ") else first?.str("Error") ?: "Open Smart lists to see why."
            one(NotifCatalog.Ids.SL_REFRESH_FAILED, rules, "sl-failed", title, text)
        }
        "RefreshRunFinished" -> {
            val failed = d.int("Failed") ?: 0
            one(
                NotifCatalog.Ids.SL_RUN_FINISHED, rules, "sl-run", "Smart lists refreshed",
                "${d.int("Succeeded") ?: 0} worked" + if (failed > 0) ", $failed failed." else ".",
            )
        }
        "TimerStopped" -> one(NotifCatalog.Ids.SL_TIMER_STOPPED, rules, "sl-timer", "Smart list refreshes stopped", d.str("Message") ?: "The plugin's timer is not running.")
        else -> EventResult()
    }

    // ---- Plugin manager (GitHub Repo Plugin Install) -------------------------------------------------------

    private fun pluginManager(event: String, d: JsonObject?, rules: List<NotifRule>): EventResult = when (event) {
        "RunCompleted" -> {
            val failed = d.int("Failed") ?: 0
            val waiting = d.int("Waiting") ?: 0
            val updated = d.int("Updated") ?: 0
            val checkOnly = (d?.get("CheckOnly") as? JsonPrimitive)?.booleanOrNull == true
            var out = one(
                NotifCatalog.Ids.PM_RUN_FINISHED, rules, "pm-run", if (checkOnly) "Plugin check finished" else "Plugin update run finished",
                "$updated updated, ${d.int("UpToDate") ?: 0} up to date, $failed failed" + if (waiting > 0) ", $waiting waiting for you." else ".",
            )
            if (failed > 0) {
                val names = names(d.array("FailedPlugins"))
                out += one(NotifCatalog.Ids.PM_UPDATE_FAILED, rules, "pm-failed", if (failed == 1) "A plugin update failed" else "$failed plugin updates failed", names.ifEmpty { "Open the plugin manager to see why." })
            }
            if (waiting > 0) {
                out += one(NotifCatalog.Ids.PM_UPDATES_READY, rules, "pm-ready", if (waiting == 1) "A plugin update is ready" else "$waiting plugin updates are ready", if (waiting == 1) "It is waiting for you in the plugin manager (Server tab)." else "They are waiting for you in the plugin manager (Server tab).")
            }
            out
        }
        "RestartPending" -> {
            val names = names(d.array("UpdatedPlugins"))
            one(NotifCatalog.Ids.PM_RESTART, rules, "pm-restart", "Emby needs a restart", if (names.isEmpty()) "Plugin changes are waiting for it." else "$names are waiting for it.")
        }
        else -> EventResult()
    }

    /**
     * Scheduled tasks as Emby pushes them ("ScheduledTasksInfo": a list). [lastEnds] maps a task id to the end time of the last failure already
     * announced; the first look only records. Returns the notifications and the new map.
     */
    fun tasks(data: JsonElement?, rules: List<NotifRule>, lastEnds: Map<String, String>, firstLook: Boolean): Pair<EventResult, Map<String, String>> {
        val array = data as? JsonArray ?: return EventResult() to lastEnds
        val next = HashMap(lastEnds)
        val out = ArrayList<NotifDelivery>()
        for (e in array) {
            val t = e as? JsonObject ?: continue
            val id = t.str("Id") ?: continue
            val last = t["LastExecutionResult"] as? JsonObject ?: continue
            if (last.str("Status") != "Failed") continue
            val end = last.str("EndTimeUtc") ?: continue
            if (next[id] == end) continue
            next[id] = end
            if (firstLook) continue
            val name = t.str("Name") ?: "A scheduled task"
            for (rule in on(rules, NotifCatalog.Ids.SV_TASK_FAILED)) out.add(make(NotifCatalog.Ids.SV_TASK_FAILED, rule, "sv-task|$id", "$name failed", "Open the Tasks tab to see it."))
        }
        return EventResult(out) to next
    }

    // ---- Tracearr ------------------------------------------------------------------------------------------

    private val SEVERITY = listOf("low", "warning", "high")

    /** New violations (not yet announced) as notifications. [seen] holds the ids already announced; the first look only records. */
    fun violations(list: List<TrViolation>, rules: List<NotifRule>, seen: Set<String>, firstLook: Boolean): Pair<EventResult, Set<String>> {
        val next = seen.toMutableSet()
        val out = ArrayList<NotifDelivery>()
        for (v in list) {
            if (!next.add(v.id) || firstLook) continue
            for (rule in on(rules, NotifCatalog.Ids.TR_VIOLATION)) {
                val min = SEVERITY.indexOf(rule.text("severity", "low")).coerceAtLeast(0)
                val rank = SEVERITY.indexOf(v.severity)
                if (rank in 0 until min) continue
                out.add(
                    make(
                        NotifCatalog.Ids.TR_VIOLATION, rule, "tr-v|${v.id}",
                        "${v.ruleName.ifEmpty { "A rule" }} was broken",
                        listOf(v.username, v.mediaTitle, v.serverName).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Open Activity in Tracearr's tab." },
                    ),
                )
            }
        }
        return EventResult(out) to next
    }

    /** A "server.health" event: [serverId] and its [status] ("up" is healthy). Fires when a server goes down, clears when it is up. */
    fun serverHealth(serverId: String, serverName: String, status: String, rules: List<NotifRule>): EventResult {
        val rulesOn = on(rules, NotifCatalog.Ids.TR_SERVER_DOWN)
        return if (status == "up") {
            EventResult(clears = rulesOn.map { "tr-down|$serverId|${it.id}" })
        } else {
            EventResult(rulesOn.map { make(NotifCatalog.Ids.TR_SERVER_DOWN, it, "tr-down|$serverId", "${serverName.ifEmpty { "A server" }} is down", "Tracearr reports it as $status.") })
        }
    }

    // ---- the subscription message ------------------------------------------------------------------------

    /** What a plugin's "<Plugin>Events" message carries (docs/push-contract.md): the events after the last number the app has, the latest number, or an error. */
    data class Envelope(val service: NotifService, val events: List<Pair<String, JsonObject?>>, val latestSeq: Long?, val error: String?)

    /** The "<Plugin>Events" message as an [Envelope], or null when [type] is not one of those. */
    fun parseEnvelope(type: String, data: JsonElement?): Envelope? {
        val service = NotifService.entries.firstOrNull { it.eventsName != null && type == it.eventsName + "Events" } ?: return null
        val obj = data as? JsonObject ?: return Envelope(service, emptyList(), null, null)
        val events = obj.array("Events").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o.str("Name") ?: return@mapNotNull null
            name to (o["Data"] as? JsonObject)
        }
        return Envelope(service, events, (obj["LatestSeq"] as? JsonPrimitive)?.longOrNull, obj.str("Error"))
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private fun names(array: List<JsonElement>): String = array.mapNotNull {
        when (it) {
            is JsonPrimitive -> it.contentOrNull
            is JsonObject -> it.str("Name") ?: it.str("name") ?: it.str("Plugin")
            else -> null
        }
    }.take(4).joinToString(", ")

    private fun minutesSince(since: String?, now: String?): Long? {
        val a = since?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val b = now?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()
        return (b.epochSecond - a.epochSecond) / 60
    }

    private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.int(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.intOrNull
    private fun JsonObject?.array(key: String): List<JsonElement> = (this?.get(key) as? JsonArray).orEmpty()

    /** An event of the catch-up route: its message name and its data. */
    fun parseCaughtUp(text: String): List<Pair<String, JsonObject?>>? {
        val root = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val events = root["Events"] as? JsonArray ?: return null
        return events.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val name = o.str("Name") ?: return@mapNotNull null
            name to (o["Data"] as? JsonObject)
        }
    }

    /** True when the catch-up answer says the log no longer reaches back far enough. */
    fun truncated(text: String): Boolean =
        ((runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("Truncated") as? JsonPrimitive)?.booleanOrNull == true
}
