package com.feerlessleadr.embidextrous

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.feerlessleadr.embidextrous.data.Swatch
import com.feerlessleadr.embidextrous.ui.colorSchemeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Hall Monitor palette is hand-picked, so its text colours are checked for contrast (WCAG AA is 4.5:1 for normal text). */
class PaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    private fun check(dark: Boolean) {
        val s = colorSchemeFor(Swatch.HallMonitor, dark)
        val pairs = mapOf(
            "text on the background" to (s.onBackground to s.background),
            "text on a surface" to (s.onSurface to s.surface),
            "secondary text on a surface" to (s.onSurfaceVariant to s.surface),
            "text on the primary colour" to (s.onPrimary to s.primary),
            "text on the primary container" to (s.onPrimaryContainer to s.primaryContainer),
            "text on the secondary colour" to (s.onSecondary to s.secondary),
            "text on the secondary container" to (s.onSecondaryContainer to s.secondaryContainer),
            "text on the tertiary container" to (s.onTertiaryContainer to s.tertiaryContainer),
            "primary as text or icons on the background" to (s.primary to s.background),
            "secondary as text or icons on the background" to (s.secondary to s.background),
        )
        for ((what, colours) in pairs) {
            val ratio = contrast(colours.first, colours.second)
            assertTrue("${if (dark) "dark" else "light"}: $what has only ${"%.1f".format(ratio)}:1", ratio >= 4.5f)
        }
    }

    @Test fun darkHallMonitorColoursAreReadable() = check(dark = true)

    @Test fun lightHallMonitorColoursAreReadable() = check(dark = false)

    @Test fun theDarkPaletteIsTheLogoPalette() {
        val s = colorSchemeFor(Swatch.HallMonitor, dark = true)
        assertEquals(Color(0xFF140B2E), s.background)
        assertEquals(Color(0xFFB388FF), s.primary)
        assertEquals(Color(0xFF5CF2C0), s.secondary)
    }

    @Test fun hallMonitorIsTheFirstSwatchAndTheDefault() {
        assertEquals(Swatch.HallMonitor, Swatch.entries.first())
    }
}
