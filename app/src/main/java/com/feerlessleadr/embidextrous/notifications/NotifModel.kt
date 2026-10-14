package com.feerlessleadr.embidextrous.notifications

import com.feerlessleadr.embidextrous.data.AppProfile
import com.feerlessleadr.embidextrous.data.Profiles
import kotlinx.serialization.Serializable

/**
 * The services that can send notifications. [channelId] is the Android notification channel (sound and vibration are set per channel in
 * Android's own settings); [needsAdmin] services need an Emby administrator account and a profile that shows the administrator tabs.
 */
enum class NotifService(
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val needsAdmin: Boolean,
    val apiBase: String? = null,
    /** The plugin name in its websocket messages: the app subscribes with "<name>EventsStart" and gets "<name>Events" (docs/push-contract.md). */
    val eventsName: String? = null,
) {
    HallMonitor("Hall Monitor", "alerts", "Hall Monitor", needsAdmin = false),
    LabelMaker("Label Maker", "music-ingest", "Music ingest (Label Maker)", needsAdmin = true, apiBase = "/emby/LabelMaker", eventsName = "LabelMaker"),
    PluginManager("Plugin manager", "plugin-updates", "Plugin updates", needsAdmin = true, apiBase = "/emby/Plugins/GitHubRepoPluginInstall", eventsName = "GitHubRepoPluginInstall"),
    HallPass("Hall Pass", "hall-pass", "Explicit music (Hall Pass)", needsAdmin = true, apiBase = "/emby/HallPass", eventsName = "HallPass"),
    SmartLists("Smart lists", "smart-lists", "Smart lists", needsAdmin = true, apiBase = "/emby/Plugins/SmartLists", eventsName = "SmartLists"),
    Tracearr("Tracearr", "tracearr", "Tracearr", needsAdmin = true),
    Server("Emby server", "server", "Emby server", needsAdmin = true),
    ;

    /** The plugin's info route (SmartLists spells it in lower case). */
    val infoPath: String? get() = apiBase?.let { it + if (it.startsWith("/emby/Plugins/")) "/info" else "/Info" }

    companion object {
        fun fromName(name: String?): NotifService? = entries.firstOrNull { it.name == name }
    }
}

/** Which services a phone can configure: Hall Monitor for everyone; the rest follow the rule that decides the Library and Server tabs. */
object NotifAvailability {
    fun services(profile: AppProfile?, isAdministrator: Boolean, customSmartLists: Boolean): List<NotifService> {
        val admin = Profiles.showsAdminCards(profile, isAdministrator, customSmartLists)
        return NotifService.entries.filter { !it.needsAdmin || admin }
    }
}

enum class ParamType { Int, Bool, Text, Choice }

/** One setting of an alert type, for example "percent" of a percentage alert. [choices] are value to label pairs. */
data class ParamSpec(
    val name: String,
    val label: String,
    val type: ParamType,
    val min: Int = 0,
    val max: Int = 0,
    val default: String = "",
    val choices: List<Pair<String, String>> = emptyList(),
)

/**
 * One kind of alert a service can send. A rule is an instance of it with its own parameter values. [unique] kinds have no parameters
 * worth repeating, so a phone has at most one rule for them.
 */
data class EventSpec(
    val id: String,
    val service: NotifService,
    val title: String,
    val description: String,
    val params: List<ParamSpec> = emptyList(),
    val defaultOn: Boolean = true,
    val lockScreenText: String,
    /** Where tapping the notification goes inside the app, as "group/tab" ("watching/hall", "library/hallpass", ...), or null for the app's front. */
    val opensTab: String? = null,
    /** True when the alert is about one user, so a rule can be limited to chosen users. */
    val perUser: Boolean = false,
    /** True when the alert has a baseline: the first look after a rule is switched on only records, so an old state never alerts. */
    val baseline: Boolean = false,
) {
    val unique: Boolean get() = params.isEmpty()
    fun defaultParams(): Map<String, String> = params.associate { it.name to it.default }
}

/** One notification rule, kept on this phone. [users] empty means every user (only used by per-user alert types). */
@Serializable
data class NotifRule(
    val id: String,
    val event: String,
    val enabled: Boolean = true,
    val params: Map<String, String> = emptyMap(),
    val users: List<String> = emptyList(),
) {
    fun int(name: String, fallback: Int): Int = params[name]?.toIntOrNull() ?: fallback
    fun text(name: String, fallback: String = ""): String = params[name] ?: fallback
    fun bool(name: String, fallback: Boolean): Boolean = params[name]?.let { it == "true" } ?: fallback
    fun appliesTo(userId: String): Boolean = users.isEmpty() || userId in users
}

/** The settings of one service on this phone. A service that is off opens no connection and sends nothing. */
@Serializable
data class ServiceSettings(val enabled: Boolean = false)

/** Quiet hours: between [startMinute] and [endMinute] (minutes after midnight; the span may cross midnight) alerts arrive without sound. */
@Serializable
data class QuietHours(val enabled: Boolean = false, val startMinute: Int = 22 * 60, val endMinute: Int = 7 * 60) {
    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!enabled || startMinute == endMinute) return false
        return if (startMinute < endMinute) minuteOfDay in startMinute until endMinute else minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

/** A notification ready to show. The Android layer turns it into a real notification. */
data class NotifDelivery(
    val service: NotifService,
    val ruleId: String,
    /** Same key for the same subject, so a later alert replaces an earlier one and a recovery can remove it. */
    val key: String,
    val title: String,
    val text: String,
    val lockScreenText: String,
    val opensTab: String? = null,
    /** Hall Monitor's "used all" alert: add-time buttons for this user. */
    val addTimeFor: Pair<String, String>? = null,
)
