package com.feerlessleadr.embidextrous.notifications

import android.content.Context
import com.feerlessleadr.embidextrous.AppContainer
import com.feerlessleadr.embidextrous.data.ApiException
import com.feerlessleadr.embidextrous.data.AppJson
import com.feerlessleadr.embidextrous.data.TracearrClient
import com.feerlessleadr.embidextrous.watching.WatchState
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Where every notification is decided and shown. The background service feeds it live data (Hall Monitor's status, pushed plugin events, Tracearr's
 * stream) and the 15-minute backup job feeds it catch-up answers; both end up here, so the same rules, the same "seen" bookkeeping and the same
 * notifications apply whichever way the news arrived. Push is the main path: the catch-up only fills what a missed push left out.
 */
class NotifHub(private val container: AppContainer, private val context: Context) {
    private val settings = container.settings
    private val hallMonitor = HallMonitorEngine()
    private val tracker = SeqTracker(settings.eventSeq())

    @Volatile private var tasksFirstLook = true

    /** The services this phone listens to now: available to this account and profile, and switched on. */
    fun activeServices(): Set<NotifService> {
        val session = container.session.value ?: return emptySet()
        val available = NotifAvailability.services(settings.profile.value, session.isAdministrator, settings.customSmartLists.value)
        val switches = settings.serviceSwitches.value
        return available.filter { switches[it.name] == true }.toSet()
    }

    fun activeRules(): List<NotifRule> = NotifRules.active(settings.notifRules.value, activeServices())

    /** The services that have a rule on and can actually connect now: Hall Monitor needs its key, Tracearr its address and key, the rest the sign-in. */
    fun connectionsNeeded(): Set<NotifService> {
        val inUse = NotifRules.servicesInUse(settings.notifRules.value, activeServices())
        return inUse.filter {
            when (it) {
                NotifService.HallMonitor -> container.pluginKey.value != null
                NotifService.Tracearr -> container.tracearr.value != null
                else -> true
            }
        }.toSet()
    }

    private fun deliver(result: EventResult) {
        for (key in result.clears) NotifPoster.clear(context, key)
        for (d in result.deliveries) NotifPoster.post(context, d)
    }

    // ---- Hall Monitor ---------------------------------------------------------------------------------------

    /** One look at Hall Monitor's live status. */
    fun onHallMonitor(state: WatchState, nowMs: Long = System.currentTimeMillis()) {
        if (!state.loaded) return
        val rules = activeRules().filter { NotifCatalog.spec(it.event)?.service == NotifService.HallMonitor }
        val saved = settings.percentState()
        val result = hallMonitor.update(rules, state.cards, state.link, nowMs, saved)
        if (result.state != saved) settings.savePercentState(result.state)
        deliver(EventResult(result.deliveries, result.clears))
    }

    // ---- pushed plugin and server events --------------------------------------------------------------------

    /** One message from Emby's websocket. Ignored when it is not ours, or its service is off on this phone. */
    fun onPush(type: String, data: JsonElement?) {
        if (type == "ScheduledTasksInfo") {
            val rules = activeRules()
            if (rules.none { it.event == NotifCatalog.Ids.SV_TASK_FAILED }) return
            val known = settings.percentState()
            val ends = known.filterKeys { it.startsWith("task|") }.mapKeys { it.key.removePrefix("task|") }
            val (result, next) = PluginEventEngine.tasks(data, rules, ends, tasksFirstLook)
            tasksFirstLook = false
            if (next != ends) settings.savePercentState(known.filterKeys { !it.startsWith("task|") } + next.mapKeys { "task|${it.key}" })
            deliver(result)
            return
        }
        val service = PluginEventEngine.serviceOf(type) ?: return
        if (service !in activeServices()) return
        if (!tracker.accept(service, PluginEventEngine.seqOf(data as? JsonObject))) return
        settings.saveEventSeq(tracker.snapshot())
        deliver(PluginEventEngine.handle(type, data, activeRules()))
    }

    /**
     * Asks a plugin what it sent while this phone was not listening, and shows it. The first time for a service it only notes where the plugin's log
     * stands, so switching a service on never replays old events. A plugin without the route (older, or not installed) is skipped.
     */
    suspend fun catchUp(service: NotifService) {
        if (service.apiBase == null || service !in activeServices()) return
        val api = container.api() ?: return
        repeat(5) {
            val after = tracker.lastSeen(service)
            val text = try {
                api.pluginEvents(service, after)
            } catch (e: ApiException) {
                return
            }
            val latest = latestSeq(text)
            if (after == 0L) {
                if (latest != null) tracker.accept(service, latest)
                settings.saveEventSeq(tracker.snapshot())
                return
            }
            val events = PluginEventEngine.parseCaughtUp(text).orEmpty()
            for ((name, data) in events) onPush(name, data)
            if (PluginEventEngine.truncated(text) && latest != null) {
                tracker.accept(service, latest)
                settings.saveEventSeq(tracker.snapshot())
            }
            if (events.size < 100) return
        }
    }

    /** Fetches a plugin's own alert catalog, keeps it for the settings screen, and gives any new alert type its default rule. */
    suspend fun refreshCatalog(service: NotifService) {
        if (service.apiBase == null) return
        val api = container.api() ?: return
        val text = try {
            api.pluginCatalog(service)
        } catch (e: ApiException) {
            return
        }
        val specs = NotifCatalog.parse(service, text) ?: return
        settings.saveCatalogCache(service, text)
        settings.seedNotifRules(specs)
    }

    /** The alert types of [service] as the settings screen shows them: the plugin's own catalog when it has sent one, else the built-in list. */
    fun specs(service: NotifService): List<EventSpec> =
        NotifCatalog.forService(service, settings.catalogCache(service)?.let { NotifCatalog.parse(service, it) })

    // ---- Tracearr ---------------------------------------------------------------------------------------------

    /** Looks at Tracearr's latest violations (after an event, or when a stream connects) and announces the new ones. */
    suspend fun checkViolations(client: TracearrClient) {
        val rules = activeRules()
        if (rules.none { it.event == NotifCatalog.Ids.TR_VIOLATION }) return
        val list = try {
            client.violations(pageSize = 25).items
        } catch (e: ApiException) {
            return
        }
        val known = settings.percentState()
        val seen = known.filterKeys { it.startsWith("trv|") }.keys.map { it.removePrefix("trv|") }.toSet()
        // The first time ever only records what is there, so switching it on does not announce old violations.
        val first = seen.isEmpty()
        val (result, next) = PluginEventEngine.violations(list, rules, seen, first)
        val keep = (list.map { it.id }).toSet() // forget ids that fell out of the latest page
        settings.savePercentState(known.filterKeys { !it.startsWith("trv|") } + (next intersect keep).associate { "trv|$it" to "1" })
        deliver(result)
    }

    /** Tracearr's "server.health" event. */
    fun onTracearrHealth(text: String, serverNames: Map<String, String>) {
        val obj = (runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("data") as? JsonObject ?: return
        val id = (obj["server_id"] as? JsonPrimitive)?.contentOrNull ?: return
        val status = (obj["status"] as? JsonPrimitive)?.contentOrNull ?: return
        deliver(PluginEventEngine.serverHealth(id, serverNames[id].orEmpty(), status, activeRules()))
    }

    /** Called when the background connections restart: the first look after that only records. */
    fun resetLooks() {
        tasksFirstLook = true
    }

    private fun latestSeq(text: String): Long? =
        ((runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject)?.get("LatestSeq") as? JsonPrimitive)?.longOrNull

}
