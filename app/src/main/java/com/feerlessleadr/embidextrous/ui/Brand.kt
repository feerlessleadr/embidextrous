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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.R

/** The Hall Monitor colours, used for the parts of the app that are always dark and bold (the sign-in and profile screens, the Home hero). */
object Brand {
    val Deep = Color(0xFF140B2E)
    val DeepMid = Color(0xFF2A1670)
    val Rich = Color(0xFF4527A8)
    val Violet = Color(0xFFB388FF)
    val Mint = Color(0xFF5CF2C0)
    val Track = Color(0xFF261A4A)
    val Amber = Color(0xFFFFB74D)
    val OnDark = Color(0xFFECE6FF)
    val OnDarkMuted = Color(0xFFB9AFD9)

    /** The diagonal gradient behind the hero areas. */
    val Gradient: Brush get() = Brush.linearGradient(listOf(Deep, DeepMid, Rich))

    /** The same gradient, brighter, for dark screens. */
    val GradientBright: Brush get() = Brush.linearGradient(listOf(Color(0xFF241461), Color(0xFF3B20A0), Color(0xFF5A35C8)))
}

/**
 * A dark violet backdrop with two big soft circles, one violet and one mint: the shape of the app icon. Content is drawn on top and
 * should use [Brand.OnDark] for text.
 */
@Composable
fun BrandBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Brand.Deep, Color(0xFF1B0F44), Brand.Deep)))
            .drawBehind {
                drawCircle(Brand.Violet.copy(alpha = 0.20f), radius = size.width * 0.62f, center = Offset(size.width * 0.02f, size.height * 0.04f))
                drawCircle(Brand.Mint.copy(alpha = 0.13f), radius = size.width * 0.55f, center = Offset(size.width * 0.98f, size.height * 0.10f))
                drawCircle(Brand.Violet.copy(alpha = 0.10f), radius = size.width * 0.5f, center = Offset(size.width * 0.9f, size.height * 0.95f))
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
