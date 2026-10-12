package com.feerlessleadr.embidextrous.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.feerlessleadr.embidextrous.data.Swatch
import com.feerlessleadr.embidextrous.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E7B34),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F2B6),
    onPrimaryContainer = Color(0xFF00210A),
    secondary = Color(0xFF526350),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D0),
    onSecondaryContainer = Color(0xFF111F10),
    tertiary = Color(0xFF8A2D6B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8EC),
    onTertiaryContainer = Color(0xFF3A002B),
    background = Color(0xFFFBFDF7),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFFBFDF7),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BF27A),
    onPrimary = Color(0xFF00390F),
    primaryContainer = Color(0xFF005318),
    onPrimaryContainer = Color(0xFFB7F2B6),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF243424),
    secondaryContainer = Color(0xFF3A4B39),
    onSecondaryContainer = Color(0xFFD5E8D0),
    tertiary = Color(0xFFFFAED9),
    onTertiary = Color(0xFF5B0F45),
    tertiaryContainer = Color(0xFF732A5A),
    onTertiaryContainer = Color(0xFFFFD8EC),
    background = Color(0xFF10140F),
    onBackground = Color(0xFFE1E3DC),
    surface = Color(0xFF10140F),
    onSurface = Color(0xFFE1E3DC),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388),
    error = Color(0xFFFFB4AB),
)

/** True when the app should draw dark for [mode] (the phone's own setting decides for [ThemeMode.System]). */
@Composable
fun useDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** True when this phone can offer the wallpaper colours (Material You, Android 12 and later). */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(h.mod(360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

private fun contrastRatio(a: Color, b: Color): Float {
    val hi = maxOf(a.luminance(), b.luminance())
    val lo = minOf(a.luminance(), b.luminance())
    return (hi + 0.05f) / (lo + 0.05f)
}

/** The lightest colour of this hue that still gives white text [target]:1 (hues like teal and amber need to be darker). */
private fun deepEnoughForWhite(h: Float, s: Float, startL: Float, target: Float = 4.6f): Color {
    var l = startL
    while (l > 0.05f && contrastRatio(hsl(h, s, l), Color.White) < target) l -= 0.01f
    return hsl(h, s, l)
}

/**
 * Neutral greys for every surface (the dark ones are the usual dark-mode greys), so the accent only shows where it highlights:
 * buttons, the selected tab, links, switches. The secondary colours follow the accent too, so tonal buttons look the same on every screen.
 */
fun ColorScheme.withNeutralSurfaces(dark: Boolean): ColorScheme {
    val neutral = if (dark) {
        copy(
            background = Color(0xFF121214), onBackground = Color(0xFFE6E3EA), surface = Color(0xFF121214), onSurface = Color(0xFFE6E3EA),
            surfaceVariant = Color(0xFF2A2A30), onSurfaceVariant = Color(0xFFC9C7D0), outline = Color(0xFF8F8D98), outlineVariant = Color(0xFF3B3B44),
            surfaceContainerLowest = Color(0xFF0D0D10), surfaceContainerLow = Color(0xFF17171B), surfaceContainer = Color(0xFF1C1C21),
            surfaceContainerHigh = Color(0xFF242429), surfaceContainerHighest = Color(0xFF2C2C32),
            inverseSurface = Color(0xFFE6E3EA), inverseOnSurface = Color(0xFF2A2A30),
        )
    } else {
        copy(
            background = Color(0xFFF7F7F9), onBackground = Color(0xFF1B1B1F), surface = Color(0xFFF7F7F9), onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE6E6EB), onSurfaceVariant = Color(0xFF46464F), outline = Color(0xFF7A7A86), outlineVariant = Color(0xFFCBCBD3),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9F9FB), surfaceContainer = Color(0xFFF2F2F5),
            surfaceContainerHigh = Color(0xFFECECF0), surfaceContainerHighest = Color(0xFFE6E6EB),
            inverseSurface = Color(0xFF2F2F35), inverseOnSurface = Color(0xFFF3F3F6),
        )
    }
    return neutral.copy(
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = primaryContainer, onSecondaryContainer = onPrimaryContainer,
        surfaceTint = neutral.surface,
    )
}

/**
 * The Hall Monitor palette, taken from the Hall Monitor logo: a deep violet night (#140B2E) with violet (#B388FF) for the day
 * and mint (#5CF2C0) for the week. Dark uses the logo's colours as they are; light keeps the same hues, darkened enough to read
 * on a pale lavender background.
 */
private val HallMonitorDark = darkColorScheme(
    primary = Color(0xFFB388FF), onPrimary = Color(0xFF2A0B5C),
    primaryContainer = Color(0xFF3A2A6E), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF5CF2C0), onSecondary = Color(0xFF00382B),
    secondaryContainer = Color(0xFF12503F), onSecondaryContainer = Color(0xFFB8F8E5),
    tertiary = Color(0xFFFFA8A8), onTertiary = Color(0xFF5B0D0D),
    tertiaryContainer = Color(0xFF6B2A2A), onTertiaryContainer = Color(0xFFFFDAD9),
    background = Color(0xFF140B2E), onBackground = Color(0xFFECE6FF),
    surface = Color(0xFF140B2E), onSurface = Color(0xFFECE6FF),
    surfaceVariant = Color(0xFF261A4A), onSurfaceVariant = Color(0xFFC9BFE6),
    outline = Color(0xFF8C80B0), outlineVariant = Color(0xFF3B2E63),
    error = Color(0xFFFFB4AB),
    surfaceTint = Color(0xFFB388FF),
    surfaceContainerLowest = Color(0xFF0F0824), surfaceContainerLow = Color(0xFF190F38), surfaceContainer = Color(0xFF1D1340),
    surfaceContainerHigh = Color(0xFF261A4A), surfaceContainerHighest = Color(0xFF2F2258),
    inverseSurface = Color(0xFFECE6FF), inverseOnSurface = Color(0xFF2A2147), inversePrimary = Color(0xFF6A3DD1),
)

private val HallMonitorLight = lightColorScheme(
    primary = Color(0xFF6A3DD1), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF230B5E),
    secondary = Color(0xFF00755A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB6F5DF), onSecondaryContainer = Color(0xFF002117),
    tertiary = Color(0xFFA7364B), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9DC), onTertiaryContainer = Color(0xFF40000F),
    background = Color(0xFFFBF8FF), onBackground = Color(0xFF1B1730),
    surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1B1730),
    surfaceVariant = Color(0xFFE8E0F7), onSurfaceVariant = Color(0xFF4A4263),
    outline = Color(0xFF7C7396), outlineVariant = Color(0xFFCBC2E0),
    error = Color(0xFFBA1A1A),
    surfaceTint = Color(0xFF6A3DD1),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF6F1FF), surfaceContainer = Color(0xFFF0EAFC),
    surfaceContainerHigh = Color(0xFFEAE3F8), surfaceContainerHighest = Color(0xFFE4DCF4),
    inverseSurface = Color(0xFF302A4A), inverseOnSurface = Color(0xFFF3EEFF), inversePrimary = Color(0xFFCDB5FF),
)

/** A whole colour scheme from one hue: Hall Monitor and Emby green keep hand-tuned colours, the others are generated. */
fun colorSchemeFor(swatch: Swatch, dark: Boolean): ColorScheme {
    val base = if (swatch == Swatch.HallMonitor) {
        if (dark) HallMonitorDark else HallMonitorLight
    } else if (swatch == Swatch.EmbyGreen) {
        if (dark) DarkColors else LightColors
    } else {
        val h = swatch.hue
        val s = swatch.saturation
        val h2 = h + 60f
        if (dark) {
            darkColorScheme(
                primary = hsl(h, (s + 0.2f).coerceAtMost(0.95f), 0.74f), onPrimary = hsl(h, s, 0.14f),
                primaryContainer = hsl(h, s, 0.25f), onPrimaryContainer = hsl(h, s * 0.8f, 0.88f),
                secondary = hsl(h, s * 0.3f, 0.78f), onSecondary = hsl(h, s * 0.3f, 0.2f),
                secondaryContainer = hsl(h, s * 0.25f, 0.28f), onSecondaryContainer = hsl(h, s * 0.3f, 0.88f),
                tertiary = hsl(h2, 0.7f, 0.8f), onTertiary = hsl(h2, 0.4f, 0.2f),
                tertiaryContainer = hsl(h2, 0.4f, 0.3f), onTertiaryContainer = hsl(h2, 0.6f, 0.9f),
                background = hsl(h, (s * 0.25f).coerceAtMost(0.2f), 0.07f), onBackground = hsl(h, 0.08f, 0.89f),
                surface = hsl(h, (s * 0.25f).coerceAtMost(0.2f), 0.07f), onSurface = hsl(h, 0.08f, 0.89f),
                surfaceVariant = hsl(h, s * 0.15f, 0.27f), onSurfaceVariant = hsl(h, 0.08f, 0.78f),
                outline = hsl(h, 0.06f, 0.58f), error = Color(0xFFFFB4AB),
            )
        } else {
            lightColorScheme(
                primary = deepEnoughForWhite(h, s, 0.40f), onPrimary = Color.White,
                primaryContainer = hsl(h, s * 0.9f, 0.9f), onPrimaryContainer = hsl(h, s, 0.1f),
                secondary = hsl(h, s * 0.3f, 0.38f), onSecondary = Color.White,
                secondaryContainer = hsl(h, s * 0.35f, 0.89f), onSecondaryContainer = hsl(h, s * 0.4f, 0.12f),
                tertiary = hsl(h2, 0.45f, 0.38f), onTertiary = Color.White,
                tertiaryContainer = hsl(h2, 0.85f, 0.9f), onTertiaryContainer = hsl(h2, 0.6f, 0.12f),
                background = hsl(h, (s * 0.2f).coerceAtMost(0.25f), 0.985f), onBackground = hsl(h, 0.08f, 0.1f),
                surface = hsl(h, (s * 0.2f).coerceAtMost(0.25f), 0.985f), onSurface = hsl(h, 0.08f, 0.1f),
                surfaceVariant = hsl(h, s * 0.2f, 0.89f), onSurfaceVariant = hsl(h, 0.08f, 0.27f),
                outline = hsl(h, 0.06f, 0.46f), error = Color(0xFFBA1A1A),
            )
        }
    }
    return base.withNeutralSurfaces(dark)
}

/** The colour of a swatch as a dot or marker (the same in light and dark, mid-tone). */
fun Swatch.dotColor(): Color = if (this == Swatch.HallMonitor) Color(0xFF8F5CF7) else if (saturation < 0.2f) hsl(hue, 0.06f, 0.5f) else hsl(hue, 0.7f, 0.5f)

/**
 * The app's colours: an accent scheme (Emby green by default) or, when [dynamic] is on and the phone supports it, the
 * wallpaper colours.
 */
@Composable
fun EmbidextrousTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Swatch = Swatch.HallMonitor,
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = useDarkTheme(mode)
    val context = LocalContext.current
    val scheme = if (dynamic && dynamicColorSupported) {
        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).withNeutralSurfaces(dark)
    } else {
        colorSchemeFor(accent, dark)
    }
    // The accent in its light form, for the always-dark screens: the dark scheme's primary (the wallpaper one when that is chosen).
    val onDark = if (dynamic && dynamicColorSupported) dynamicDarkColorScheme(context).primary else colorSchemeFor(accent, true).primary
    androidx.compose.runtime.CompositionLocalProvider(LocalAccentOnDark provides onDark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}