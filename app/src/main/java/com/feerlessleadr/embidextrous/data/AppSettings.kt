package com.feerlessleadr.embidextrous.data

import android.content.SharedPreferences
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

    private val _accent = MutableStateFlow(Swatch.fromName(prefs.getString(KEY_ACCENT, null)) ?: Swatch.EmbyGreen)
    val accent: StateFlow<Swatch> = _accent.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))

    /** Use the phone's wallpaper colours (Material You, Android 12 and later) instead of [accent]. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _density = MutableStateFlow(Density.fromName(prefs.getString(KEY_DENSITY, null)))
    val density: StateFlow<Density> = _density.asStateFlow()

    private val _listColors = MutableStateFlow(readListColors())

    /** The colour a list was marked with, by list id. Kept on this phone only: the plugin has no field for it. */
    val listColors: StateFlow<Map<String, Swatch>> = _listColors.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ALERTS, false))

    /** Watching Eye: keep a live connection in the background and notify when someone reaches their limit. */
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _widgetHideNames = MutableStateFlow(prefs.getBoolean(KEY_WIDGET_HIDE_NAMES, false))

    /** Watching Eye: the home-screen widget shows "User 1", "User 2" instead of names. */
    val widgetHideNames: StateFlow<Boolean> = _widgetHideNames.asStateFlow()

    fun setAlertsEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS, on).apply()
        _alertsEnabled.value = on
    }

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
        const val KEY_ALERTS = "watching_alerts"
        const val KEY_WIDGET_HIDE_NAMES = "watching_widget_hide_names"
    }
}