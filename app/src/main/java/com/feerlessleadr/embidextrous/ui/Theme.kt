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

/** The surface layers (menus, dialogs, sheets) tinted with the accent, instead of Material's default lilac grey. */
private fun ColorScheme.withTintedSurfaces(swatch: Swatch, dark: Boolean): ColorScheme {
    val h = swatch.hue
    val s = (swatch.saturation * 0.25f).coerceAtMost(0.2f)
    return if (dark) {
        copy(
            surfaceContainerLowest = hsl(h, s, 0.05f), surfaceContainerLow = hsl(h, s, 0.09f), surfaceContainer = hsl(h, s, 0.115f),
            surfaceContainerHigh = hsl(h, s, 0.15f), surfaceContainerHighest = hsl(h, s, 0.19f), surfaceTint = primary,
            inverseSurface = hsl(h, s, 0.88f), inverseOnSurface = hsl(h, 0.1f, 0.15f), inversePrimary = hsl(h, swatch.saturation, 0.3f),
        )
    } else {
        copy(
            surfaceContainerLowest = hsl(h, s, 1f), surfaceContainerLow = hsl(h, s, 0.965f), surfaceContainer = hsl(h, s, 0.945f),
            surfaceContainerHigh = hsl(h, s, 0.925f), surfaceContainerHighest = hsl(h, s, 0.905f), surfaceTint = primary,
            inverseSurface = hsl(h, s, 0.17f), inverseOnSurface = hsl(h, 0.1f, 0.93f), inversePrimary = hsl(h, (swatch.saturation + 0.2f).coerceAtMost(0.95f), 0.72f),
        )
    }
}

/** A whole colour scheme from one hue: Emby green keeps its hand-tuned colours, the others are generated. */
fun colorSchemeFor(swatch: Swatch, dark: Boolean): ColorScheme {
    val base = if (swatch == Swatch.EmbyGreen) {
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
    return base.withTintedSurfaces(swatch, dark)
}

/** The colour of a swatch as a dot or marker (the same in light and dark, mid-tone). */
fun Swatch.dotColor(): Color = if (saturation < 0.2f) hsl(hue, 0.06f, 0.5f) else hsl(hue, 0.7f, 0.5f)

/**
 * The app's colours: an accent scheme (Emby green by default) or, when [dynamic] is on and the phone supports it, the
 * wallpaper colours.
 */
@Composable
fun EmbidextrousTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Swatch = Swatch.EmbyGreen,
    dynamic: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = useDarkTheme(mode)
    val context = LocalContext.current
    val scheme = if (dynamic && dynamicColorSupported) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorSchemeFor(accent, dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}