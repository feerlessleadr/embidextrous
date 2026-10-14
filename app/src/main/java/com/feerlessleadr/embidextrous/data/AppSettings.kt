package com.feerlessleadr.embidextrous.data

import android.content.SharedPreferences
import com.feerlessleadr.embidextrous.notifications.NotifRule
import com.feerlessleadr.embidextrous.notifications.NotifRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Which colours the app uses: follow the phone's setting, or always light or dark. */
enum class ThemeMode(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark"),
    ;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}

/**
 * A curated colour: the app's accent (a whole colour scheme is generated from [hue] and [saturation] for light and
 * dark) and the dot a list can be marked with. A free colour picker was rejected: arbitrary colours break contrast.
 */
enum class Swatch(val label: String, val hue: Float, val saturation: Float) {
    HallMonitor("Hall Monitor", 263f, 0.90f),
    EmbyGreen("Emby green", 130f, 0.60f),
    Ocean("Ocean", 210f, 0.65f),
    Teal("Teal", 175f, 0.60f),
    Violet("Violet", 265f, 0.55f),
    Rose("Rose", 335f, 0.60f),
    Crimson("Crimson", 5f, 0.65f),
    Amber("Amber", 38f, 0.80f),
    Graphite("Graphite", 220f, 0.08f),
    ;

    companion object {
        fun fromName(name: String?): Swatch? = entries.firstOrNull { it.name == name }
    }
}

/** How much the list overview shows per list. */
enum class Density(val label: String) {
    Comfortable("Comfortable"),
    Compact("Compact"),
    ;

    companion object {
        fun fromName(name: String?): Density = entries.firstOrNull { it.name == name } ?: Comfortable
    }
}

/** Preferences that are not secret (the secret token lives in [SessionRepository]). */
class AppSettings(private val prefs: SharedPreferences) {
    private val _themeMode = MutableStateFlow(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accent = MutableStateFlow(Swatch.fromName(prefs.getString(KEY_ACCENT, null)) ?: Swatch.HallMonitor)
    val accent: StateFlow<Swatch> = _accent.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))

    /** Use the phone's wallpaper colours (Material You, Android 12 and later) instead of [accent]. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _density = MutableStateFlow(Density.fromName(prefs.getString(KEY_DENSITY, null)))
    val density: StateFlow<Density> = _density.asStateFlow()

    private val _listColors = MutableStateFlow(readListColors())

    /** The colour a list was marked with, by list id. Kept on this phone only: the plugin has no field for it. */
    val listColors: StateFlow<Map<String, Swatch>> = _listColors.asStateFlow()

    private val _profile = MutableStateFlow(AppProfile.fromName(prefs.getString(KEY_PROFILE, null)))

    /** How this phone uses the app; null until the person has chosen (the app asks once after signing in). */
    val profile: StateFlow<AppProfile?> = _profile.asStateFlow()

    private val _customSmartLists = MutableStateFlow(prefs.getBoolean(KEY_CUSTOM_SMART_LISTS, false))

    /** With the Custom profile: show the Smart Lists tab. */
    val customSmartLists: StateFlow<Boolean> = _customSmartLists.asStateFlow()

    fun setProfile(profile: AppProfile) {
        prefs.edit().putString(KEY_PROFILE, profile.name).apply()
        _profile.value = profile
    }

    fun setCustomSmartLists(on: Boolean) {
        prefs.edit().putBoolean(KEY_CUSTOM_SMART_LISTS, on).apply()
        _customSmartLists.value = on
    }

    private val _homeLayout = MutableStateFlow(HomeLayout.decode(prefs.getString(KEY_HOME_LAYOUT, null)))

    /** Which Home cards show and in what order (kept on this phone). */
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()

    fun setHomeLayout(layout: HomeLayout) {
        prefs.edit().putString(KEY_HOME_LAYOUT, layout.encode()).apply()
        _homeLayout.value = layout
    }

    private val _notifRules = MutableStateFlow(loadNotifRules())

    /**
     * Notification rules (Settings, under Hall Monitor). Kept on this phone. The first run turns the old Background alerts
     * switch into the default "limit reached" rule, so existing behaviour carries over.
     */
    val notifRules: StateFlow<List<NotifRule>> = _notifRules.asStateFlow()

    fun setNotifRules(rules: List<NotifRule>) {
        prefs.edit().putString(KEY_NOTIF_RULES, NotifRules.encode(rules)).apply()
        _notifRules.value = rules
    }

    /** The "rule|user" -> day map of percent rules that already fired (kept across restarts). */
    fun percentState(): Map<String, String> {
        val text = prefs.getString(KEY_PERCENT_STATE, null) ?: return emptyMap()
        return try {
            AppJson.parseToJsonElement(text).jsonObject.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun savePercentState(state: Map<String, String>) {
        prefs.edit().putString(KEY_PERCENT_STATE, JsonObject(state.mapValues { JsonPrimitive(it.value) }).toString()).apply()
    }

    private fun loadNotifRules(): List<NotifRule> {
        NotifRules.decode(prefs.getString(KEY_NOTIF_RULES, null))?.let { return it }
        val migrated = NotifRules.defaults(legacyAlertsOn = prefs.getBoolean(KEY_ALERTS, false))
        prefs.edit().putString(KEY_NOTIF_RULES, NotifRules.encode(migrated)).apply()
        return migrated
    }

    private val _widgetHideNames = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_HIDE_NAMES, false))

    /** Hall Monitor: the home-screen widget shows "User 1", "User 2" instead of names. */
    val widgetHideNames: StateFlow<Boolean> = _widgetHideNames.asStateFlow()

    fun setWidgetHideNames(on: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_HIDE_NAMES, on).apply()
        _widgetHideNames.value = on
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAccent(swatch: Swatch) {
        prefs.edit().putString(KEY_ACCENT, swatch.name).apply()
        _accent.value = swatch
    }

    fun setDynamicColor(on: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC, on).apply()
        _dynamicColor.value = on
    }

    fun setDensity(density: Density) {
        prefs.edit().putString(KEY_DENSITY, density.name).apply()
        _density.value = density
    }

    /** Marks a list with [swatch], or clears the mark when it is null. */
    fun setListColor(listId: String, swatch: Swatch?) {
        val next = _listColors.value.toMutableMap()
        if (swatch == null) next.remove(listId) else next[listId] = swatch
        _listColors.value = next
        prefs.edit().putString(KEY_LIST_COLORS, JsonObject(next.mapValues { JsonPrimitive(it.value.name) }).toString()).apply()
    }

    private fun readListColors(): Map<String, Swatch> {
        val text = prefs.getString(KEY_LIST_COLORS, null) ?: return emptyMap()
        return try {
            AppJson.parseToJsonElement(text).jsonObject.mapNotNull { (id, v) -> Swatch.fromName((v as? JsonPrimitive)?.contentOrNull)?.let { id to it } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_DENSITY = "density"
        const val KEY_LIST_COLORS = "list_colors"
        const val KEY_PROFILE = "profile"
        const val KEY_CUSTOM_SMART_LISTS = "custom_smart_lists"
        const val KEY_HOME_LAYOUT = "home_layout"
        /** The old Background alerts switch: read once, to migrate it into the default rule. */
        const val KEY_ALERTS = "watching_alerts"
        const val KEY_NOTIF_RULES = "notif_rules"
        const val KEY_PERCENT_STATE = "notif_percent_state"
        const val KEY_WIDGET_HIDE_NAMES = "watching_widget_hide_names"
    }
}