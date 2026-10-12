package com.feerlessleadr.embidextrous.watching

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The Hall Monitor tab is a lot of cards with numbers on them, so it stays calm: neutral greys for the background, the cards and
 * the tonal buttons, with the chosen accent colour kept for the few things that should stand out (the progress bar, the main
 * button, links). The Smart Lists tab keeps the full accent look.
 */
internal fun ColorScheme.calm(): ColorScheme {
    val dark = background.luminance() < 0.5f
    return if (dark) {
        copy(
            background = Color(0xFF121212), surface = Color(0xFF121212), onSurface = Color(0xFFE6E6E6), onBackground = Color(0xFFE6E6E6),
            surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFB8B8B8), outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF444444),
            surfaceContainerLowest = Color(0xFF0D0D0D), surfaceContainerLow = Color(0xFF171717), surfaceContainer = Color(0xFF1C1C1C),
            surfaceContainerHigh = Color(0xFF222222), surfaceContainerHighest = Color(0xFF242424), surfaceTint = Color(0xFF121212),
            secondaryContainer = Color(0xFF383838), onSecondaryContainer = Color(0xFFEDEDED),
            primaryContainer = Color(0xFF383838), onPrimaryContainer = Color(0xFFEDEDED),
        )
    } else {
        copy(
            background = Color(0xFFF2F2F2), surface = Color(0xFFF2F2F2), onSurface = Color(0xFF1C1C1C), onBackground = Color(0xFF1C1C1C),
            surfaceVariant = Color(0xFFE4E4E4), onSurfaceVariant = Color(0xFF4A4A4A), outline = Color(0xFF7A7A7A), outlineVariant = Color(0xFFCFCFCF),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7F7), surfaceContainer = Color(0xFFEFEFEF),
            surfaceContainerHigh = Color(0xFFE9E9E9), surfaceContainerHighest = Color(0xFFFFFFFF), surfaceTint = Color(0xFFF2F2F2),
            secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Color(0xFF1C1C1C),
            primaryContainer = Color(0xFFE2E2E2), onPrimaryContainer = Color(0xFF1C1C1C),
        )
    }
}

@Composable
internal fun WatchingEyeTheme(content: @Composable () -> Unit) {
    // With the Hall Monitor palette the whole app already has restrained, designed surfaces, so nothing is overridden. Another
    // accent colour (or the wallpaper colours) gets the neutral treatment here so it does not flood the cards.
    if (com.feerlessleadr.embidextrous.ui.LocalFullPalette.current) {
        content()
    } else {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.calm(), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}
