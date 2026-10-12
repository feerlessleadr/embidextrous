package com.feerlessleadr.embidextrous.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.R

/**
 * Fixed colours (the logo's, and the meaning colours for good and warning). Everything that is a highlight, such as buttons, rings
 * and badges, follows the accent the person chose; see [accentOnDark] and [onAccent].
 */
object Brand {
    val Deep = Color(0xFF140B2E)
    val Violet = Color(0xFFB388FF)
    val Mint = Color(0xFF5CF2C0)
    val Amber = Color(0xFFFFB74D)
    val OnDark = Color(0xFFEAE8EF)
    val OnDarkMuted = Color(0xFFB9B7C2)
}

/** The chosen accent, light enough to stand on a dark area (on a dark screen that is the accent itself; on a light screen a lightened copy). */
@Composable
fun accentOnDark(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) scheme.primary else lerp(scheme.primary, Color.White, 0.45f)
}

/** Text or icon colour that reads on top of [accent] (used on filled accent buttons over dark areas). */
fun onAccent(accent: Color): Color = if (accent.luminance() > 0.3f) Color(0xFF1B1030) else Color.White

/**
 * The hero look: a dark neutral card with a soft wash of the accent in one corner (brighter, and with a thin accent outline, on a
 * dark screen so its edge shows). Always dark, so light text goes on it.
 */
@Composable
fun Modifier.heroSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val accent = accentOnDark()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val start = if (dark) Color(0xFF24242C) else Color(0xFF17171D)
    val mid = if (dark) Color(0xFF2B2B35) else Color(0xFF1E1E26)
    val brush = Brush.linearGradient(listOf(start, lerp(mid, accent, 0.07f), lerp(mid, accent, 0.22f)))
    val filled = clip(shape).background(brush)
    return if (dark) filled.border(1.dp, accent.copy(alpha = 0.4f), shape) else filled
}

/**
 * A near-black backdrop with two big soft circles in the accent, the shape of the app icon. Content is drawn on top and should use
 * [Brand.OnDark] for text.
 */
@Composable
fun BrandBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val accent = accentOnDark()
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF0E0E12), Color(0xFF17171D), Color(0xFF0E0E12))))
            .drawBehind {
                drawCircle(accent.copy(alpha = 0.16f), radius = size.width * 0.62f, center = Offset(size.width * 0.02f, size.height * 0.04f))
                drawCircle(accent.copy(alpha = 0.09f), radius = size.width * 0.55f, center = Offset(size.width * 0.98f, size.height * 0.10f))
                drawCircle(accent.copy(alpha = 0.07f), radius = size.width * 0.5f, center = Offset(size.width * 0.9f, size.height * 0.95f))
            },
        content = content,
    )
}

/** The app icon as a rounded tile. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.26f)
    Box(modifier.size(size).clip(shape).background(Brand.Deep).border(1.dp, Brand.Violet.copy(alpha = 0.4f), shape)) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** An icon in a softly tinted circle: how the cards mark what they are about. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** A small rounded label ("Watching now", "Update available") in [color]. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = color.copy(alpha = 0.16f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
