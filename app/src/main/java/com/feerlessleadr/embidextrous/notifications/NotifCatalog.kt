package com.feerlessleadr.embidextrous.notifications

import com.feerlessleadr.embidextrous.data.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Every alert type the app knows, per service. Plugins that publish a catalog (Label Maker, Hall Pass, Smart lists) can replace their list; these are the fallback. */
object NotifCatalog {
    object Ids {
        const val HM_PERCENT = "HallMonitor.PercentUsed"
        const val HM_ALL_USED = "HallMonitor.AllTimeUsed"
        const val HM_TIMEOUT_STARTED = "HallMonitor.TimeOutStarted"
        const val HM_TIMEOUT_ENDED = "HallMonitor.TimeOutEnded"
        const val HM_OUTSIDE_HOURS = "HallMonitor.StoppedOutsideHours"
        const val HM_STARTED = "HallMonitor.StartedWatching"
        const val HM_EXTRA_TIME = "HallMonitor.ExtraTimeAdded"
        const val HM_CONNECTION = "HallMonitor.ConnectionProblem"
        const val HM_SUMMARY = "HallMonitor.DailySummary"
        const val LM_NEEDS_REVIEW = "LabelMaker.NeedsReview"
        const val LM_AUTO_APPLIED = "LabelMaker.AutoApplied"
        const val LM_ALBUM_FAILED = "LabelMaker.AlbumFailed"
        const val LM_LANE_PROBLEM = "LabelMaker.LaneProblem"
        const val LM_SERVICE_TROUBLE = "LabelMaker.ServiceTrouble"
        const val PM_UPDATES_READY = "PluginManager.UpdatesReady"
        const val PM_UPDATE_FAILED = "PluginManager.UpdateFailed"
        const val PM_RESTART = "PluginManager.RestartNeeded"
        const val PM_RUN_FINISHED = "PluginManager.RunFinished"
        const val HP_SCAN_FINISHED = "HallPass.ScanFinished"
        const val HP_SCAN_FAILED = "HallPass.ScanFailed"
        const val HP_APPLY_FINISHED = "HallPass.ApplyFinished"
        const val HP_APPLY_FAILED = "HallPass.ApplyFailed"
        const val HP_REVIEW_QUEUE = "HallPass.ReviewQueue"
        const val SL_REFRESH_FAILED = "SmartLists.RefreshFailed"
        const val SL_RUN_FINISHED = "SmartLists.RefreshRunFinished"
        const val SL_TIMER_STOPPED = "SmartLists.TimerStopped"
        const val TR_VIOLATION = "Tracearr.Violation"
        const val TR_SERVER_DOWN = "Tracearr.ServerDown"
        const val SV_TASK_FAILED = "Server.TaskFailed"
    }

    private val PERIODS = listOf("any" to "Any limit", "daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly", "yearly" to "Yearly")

    val builtIn: List<EventSpec> = listOf(
        // ---- Hall Monitor ----
        EventSpec(
            Ids.HM_PERCENT, NotifService.HallMonitor, "Used a set percentage of their time",
            "Sent once for each user and limit when that limit reaches the percentage, and again after the limit resets. Playback is still allowed.",
            params = listOf(
                ParamSpec("percent", "Percent used (1 to 99)", ParamType.Int, 1, 99, "80"),
                ParamSpec("period", "Which limit", ParamType.Choice, default = "any", choices = PERIODS),
            ),
            defaultOn = false, lockScreenText = "A viewing threshold was reached. Unlock to see who.", opensTab = "watching/hall", perUser = true, baseline = true,
        ),
        EventSpec(
            Ids.HM_ALL_USED, NotifService.HallMonitor, "Used all of their time",
            "Playback stops. The alert has a +30 min button to give more time (it needs an unlocked phone).",
            defaultOn = true, lockScreenText = "A viewing limit was reached. Unlock to see who.", opensTab = "watching/hall", perUser = true,
        ),
        EventSpec(
            Ids.HM_TIMEOUT_STARTED, NotifService.HallMonitor, "A time-out started",
            "Sent when someone is timed out, from any phone or the dashboard (Bedtime included).",
            defaultOn = false, lockScreenText = "A time-out started. Unlock to see who.", opensTab = "watching/hall", perUser = true,
        ),
        EventSpec(
            Ids.HM_TIMEOUT_ENDED, NotifService.HallMonitor, "A time-out ended",
            "Sent when a time-out runs out or is ended early.",
            defaultOn = false, lockScreenText = "A time-out ended. Unlock to see who.", opensTab = "watching/hall", perUser = true,
        ),
        EventSpec(
            Ids.HM_OUTSIDE_HOURS, NotifService.HallMonitor, "Stopped when the allowed hours ended",
            "Sent when someone was watching and playback was stopped because the allowed viewing hours ended.",
            defaultOn = false, lockScreenText = "Playback was stopped. Unlock to see who.", opensTab = "watching/hall", perUser = true,
        ),
        EventSpec(
            Ids.HM_STARTED, NotifService.HallMonitor, "Started watching",
            "Sent each time someone starts playing something. This can be chatty.",
            defaultOn = false, lockScreenText = "Someone started watching. Unlock to see who.", opensTab = "watching/hall", perUser = true,
        ),
        EventSpec(
            Ids.HM_EXTRA_TIME, NotifService.HallMonitor, "Extra time was added",
            "Sent when someone's daily time grows by at least a minute, from any phone or the dashboard.",
            defaultOn = false, lockScreenText = "Extra time was added. Unlock to see who.", opensTab = "watching/hall", perUser = true,
        ),
        EventSpec(
            Ids.HM_CONNECTION, NotifService.HallMonitor, "Hall Monitor cannot be reached",
            "Sent when this phone cannot reach Hall Monitor, or the key is refused, for the number of minutes you choose. A recovery clears it.",
            params = listOf(ParamSpec("minutes", "After this many minutes (1 to 240)", ParamType.Int, 1, 240, "10")),
            defaultOn = false, lockScreenText = "Hall Monitor cannot be reached.", opensTab = "watching/hall",
        ),
        EventSpec(
            Ids.HM_SUMMARY, NotifService.HallMonitor, "Daily summary",
            "One notification a day with how long each user has watched so far.",
            params = listOf(ParamSpec("time", "Time of day", ParamType.Text, default = "20:00")),
            defaultOn = false, lockScreenText = "Your daily viewing summary is ready. Unlock to read it.", opensTab = "watching/hall",
        ),
        // ---- Label Maker ----
        EventSpec(
            Ids.LM_NEEDS_REVIEW, NotifService.LabelMaker, "Albums need review",
            "Sent when downloaded albums could not be matched with certainty and wait for you.",
            params = listOf(ParamSpec("MinCount", "When this many albums need review", ParamType.Int, 1, 500, "1")),
            lockScreenText = "Music needs review. Unlock to see which.", opensTab = "library/labelmaker",
        ),
        EventSpec(
            Ids.LM_AUTO_APPLIED, NotifService.LabelMaker, "An album was tagged automatically",
            "Sent after a pass that tagged and filed albums by itself (or, in a dry run, would have). One summary per pass.",
            defaultOn = false, lockScreenText = "Music was filed. Unlock to see what.", opensTab = "library/labelmaker",
        ),
        EventSpec(
            Ids.LM_ALBUM_FAILED, NotifService.LabelMaker, "An album failed",
            "Sent when an album could not be handled and needs fixing (a damaged file, a folder that cannot be written).",
            lockScreenText = "An album failed. Unlock to see which.", opensTab = "library/labelmaker",
        ),
        EventSpec(
            Ids.LM_LANE_PROBLEM, NotifService.LabelMaker, "A lane has a problem",
            "Sent when an inbox or library folder cannot be reached or written, or the disk is nearly full. It clears itself when the problem is gone.",
            lockScreenText = "A music lane has a problem. Unlock to see which.", opensTab = "library/labelmaker",
        ),
        EventSpec(
            Ids.LM_SERVICE_TROUBLE, NotifService.LabelMaker, "Music lookups are stuck",
            "Sent when MusicBrainz could not be reached for a while and albums are waiting. It clears itself when lookups work again.",
            params = listOf(ParamSpec("Minutes", "After this many minutes", ParamType.Int, 15, 240, "15")),
            defaultOn = false, lockScreenText = "Music lookups are stuck. Unlock for details.", opensTab = "library/labelmaker",
        ),
        // ---- Plugin manager ----
        EventSpec(
            Ids.PM_UPDATES_READY, NotifService.PluginManager, "Plugin updates are ready",
            "Sent when a check finds plugin updates that are waiting for you to apply them.",
            lockScreenText = "Plugin updates are ready.", opensTab = "server/plugins",
        ),
        EventSpec(
            Ids.PM_UPDATE_FAILED, NotifService.PluginManager, "A plugin update failed",
            "Sent when a plugin could not be checked or updated.",
            lockScreenText = "A plugin update failed. Unlock to see which.", opensTab = "server/plugins",
        ),
        EventSpec(
            Ids.PM_RESTART, NotifService.PluginManager, "Emby needs a restart",
            "Sent when updated plugins are waiting for the server to restart.",
            lockScreenText = "Emby needs a restart to finish plugin updates.", opensTab = "server/plugins",
        ),
        EventSpec(
            Ids.PM_RUN_FINISHED, NotifService.PluginManager, "An update run finished",
            "Sent when a check or update run ends, with the counts.",
            defaultOn = false, lockScreenText = "A plugin update run finished.", opensTab = "server/plugins",
        ),
        // ---- Hall Pass ----
        EventSpec(
            Ids.HP_SCAN_FINISHED, NotifService.HallPass, "A scan finished",
            "Sent when a scan of the music library ends, with how many tracks need review.",
            lockScreenText = "A Hall Pass scan finished.", opensTab = "library/hallpass",
        ),
        EventSpec(
            Ids.HP_SCAN_FAILED, NotifService.HallPass, "A scan failed",
            "Sent when a scan fails or is cancelled.",
            lockScreenText = "A Hall Pass scan failed.", opensTab = "library/hallpass",
        ),
        EventSpec(
            Ids.HP_APPLY_FINISHED, NotifService.HallPass, "Apply or undo finished",
            "Sent when writing explicit tags to the music files (or undoing it) ends.",
            lockScreenText = "Hall Pass finished writing tags.", opensTab = "library/hallpass",
        ),
        EventSpec(
            Ids.HP_APPLY_FAILED, NotifService.HallPass, "Apply or undo failed",
            "Sent when a write job fails, is cancelled, or was interrupted.",
            lockScreenText = "Hall Pass could not finish writing tags.", opensTab = "library/hallpass",
        ),
        EventSpec(
            Ids.HP_REVIEW_QUEUE, NotifService.HallPass, "Tracks wait for review",
            "Sent when the number of tracks waiting for you grows past the number you choose.",
            params = listOf(ParamSpec("MinCount", "When this many tracks wait", ParamType.Int, 1, 100000, "50")),
            defaultOn = false, lockScreenText = "Tracks are waiting for review.", opensTab = "library/hallpass",
        ),
        // ---- Smart lists ----
        EventSpec(
            Ids.SL_REFRESH_FAILED, NotifService.SmartLists, "A list refresh failed",
            "Sent when a smart list could not be refreshed. At most once an hour for each list.",
            lockScreenText = "A smart list failed to refresh. Unlock to see which.", opensTab = "library/lists",
        ),
        EventSpec(
            Ids.SL_RUN_FINISHED, NotifService.SmartLists, "A refresh run finished",
            "Sent when a refresh of all lists ends, with how many worked and how many failed.",
            defaultOn = false, lockScreenText = "A smart list refresh finished.", opensTab = "library/lists",
        ),
        EventSpec(
            Ids.SL_TIMER_STOPPED, NotifService.SmartLists, "The refresh timer stopped",
            "Sent when scheduled lists are not being refreshed because the plugin's timer is not running.",
            lockScreenText = "Smart list refreshes are not running.", opensTab = "library/lists",
        ),
        // ---- Tracearr ----
        EventSpec(
            Ids.TR_VIOLATION, NotifService.Tracearr, "A rule was broken",
            "Sent when one of Tracearr's rules fires, for example too many streams at once or an unusual place.",
            params = listOf(
                ParamSpec(
                    "severity", "Lowest severity", ParamType.Choice, default = "low",
                    choices = listOf("low" to "Any", "warning" to "Warning and above", "high" to "High only"),
                ),
            ),
            lockScreenText = "Tracearr reported a rule violation. Unlock to see it.", opensTab = "watching/tracearr",
        ),
        EventSpec(
            Ids.TR_SERVER_DOWN, NotifService.Tracearr, "A server is down",
            "Sent when Tracearr sees a media server go down, and cleared when it is back.",
            lockScreenText = "Tracearr reports a server is down.", opensTab = "watching/tracearr",
        ),
        // ---- Emby server ----
        EventSpec(
            Ids.SV_TASK_FAILED, NotifService.Server, "A scheduled task failed",
            "Sent when a scheduled task ends in failure.",
            lockScreenText = "A scheduled task failed. Unlock to see which.", opensTab = "server/tasks",
        ),
    )

    /** True when a plugin's info answer lists "events": it can push alerts and be caught up. An older version answers info without it. */
    fun sendsAlerts(infoText: String): Boolean {
        val root = runCatching { AppJson.parseToJsonElement(infoText) }.getOrNull() as? JsonObject ?: return false
        val features = root["Features"] as? JsonArray ?: return false
        return features.any { (it as? JsonPrimitive)?.contentOrNull == "events" }
    }

    fun forService(service: NotifService, fetched: List<EventSpec>? = null): List<EventSpec> =
        fetched?.takeIf { it.isNotEmpty() } ?: builtIn.filter { it.service == service }

    fun spec(id: String): EventSpec? = builtIn.firstOrNull { it.id == id }

    /**
     * A plugin's own catalog (GET .../Notifications/Catalog) as event specs for [service]. Null when the answer cannot be used. Tolerant:
     * unknown fields are ignored, an unknown parameter type is read as text. The built-in entry of the same id supplies anything the plugin left out
     * (the lock-screen text, the tab it opens).
     */
    fun parse(service: NotifService, text: String): List<EventSpec>? {
        val root = runCatching { AppJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val events = root["Events"] as? JsonArray ?: return null
        val specs = events.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = o.str("Id") ?: return@mapNotNull null
            if (!id.startsWith(service.name + ".")) return@mapNotNull null
            val builtInSpec = spec(id)
            val params = (o["Params"] as? JsonArray).orEmpty().mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                val name = po.str("Name") ?: return@mapNotNull null
                val type = when (po.str("Type")?.lowercase()) {
                    "int" -> ParamType.Int
                    "bool" -> ParamType.Bool
                    "choice" -> ParamType.Choice
                    else -> ParamType.Text
                }
                ParamSpec(
                    name = name,
                    label = po.str("Label") ?: name,
                    type = type,
                    min = (po["Min"] as? JsonPrimitive)?.intOrNull ?: 0,
                    max = (po["Max"] as? JsonPrimitive)?.intOrNull ?: 0,
                    default = po.str("Default") ?: "",
                    choices = (po["Choices"] as? JsonArray).orEmpty().mapNotNull { c -> (c as? JsonPrimitive)?.contentOrNull?.let { it to it } },
                )
            }
            val defaultRule = o["DefaultRule"] as? JsonObject
            val defaultParams = defaultRule?.get("Params") as? JsonObject
            EventSpec(
                id = id,
                service = service,
                title = o.str("Title") ?: builtInSpec?.title ?: id,
                description = o.str("Description") ?: builtInSpec?.description.orEmpty(),
                params = params.map { p -> defaultParams?.str(p.name)?.let { p.copy(default = it) } ?: p },
                defaultOn = (defaultRule?.get("Enabled") as? JsonPrimitive)?.booleanOrNull ?: builtInSpec?.defaultOn ?: true,
                lockScreenText = o.str("LockScreenText") ?: builtInSpec?.lockScreenText ?: "${service.title} has news. Unlock to see it.",
                opensTab = builtInSpec?.opensTab,
            )
        }
        return specs.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
