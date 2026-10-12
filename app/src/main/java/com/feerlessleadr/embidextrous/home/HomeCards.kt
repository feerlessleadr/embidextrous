package com.feerlessleadr.embidextrous.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import com.feerlessleadr.embidextrous.ui.Brand
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.Attention
import com.feerlessleadr.embidextrous.watching.Avatar
import com.feerlessleadr.embidextrous.watching.Poster
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal val HomeCardShape = RoundedCornerShape(24.dp)
private const val QUICK_ADD = 15

/** The hero look: the violet gradient, brighter on a dark screen where it also gets a thin violet outline so its edge shows. */
@Composable
internal fun Modifier.heroSurface(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val filled = clip(shape).background(if (dark) Brand.GradientBright else Brand.Gradient)
    return if (dark) filled.border(1.dp, Brand.Violet.copy(alpha = 0.5f), shape) else filled
}

/** "Good morning", "Good afternoon" or "Good evening" for a local hour (0 to 23). */
object Greeting {
    fun forHour(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
internal fun okColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
internal fun warnColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFB74D) else Color(0xFF9A5B00)

/** A heading above a group of cards, with a small icon. */
@Composable
internal fun SectionTitle(text: String, icon: ImageVector? = null) {
    Row(Modifier.padding(start = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The big card at the top: "All good", or what needs attention, each with the one action that helps. */
@Composable
internal fun StatusCard(attention: List<Attention>, watchingCount: Int, loaded: Boolean, onAdd: (UserStatus) -> Unit, onOpenSettings: () -> Unit) {
    val greeting = remember { Greeting.forHour(LocalTime.now().hour) }
    Box(
        Modifier
            .fillMaxWidth()
            .heroSurface(RoundedCornerShape(28.dp))
            .drawBehind {
                drawCircle(Brand.Violet.copy(alpha = 0.22f), radius = size.height * 0.9f, center = Offset(size.width * 0.95f, size.height * 0.1f))
                drawCircle(Brand.Mint.copy(alpha = 0.14f), radius = size.height * 0.6f, center = Offset(size.width * 0.80f, size.height * 0.95f))
            },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(greeting, style = MaterialTheme.typography.labelLarge, color = Brand.OnDarkMuted)
            if (attention.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Default.CheckCircle, Brand.Mint, size = 52.dp)
                    Column {
                        Text(if (loaded) "All good" else "Checking…", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Brand.OnDark)
                        Text(
                            when {
                                !loaded -> "Loading the latest from the server."
                                watchingCount == 0 -> "Nobody is watching right now."
                                watchingCount == 1 -> "One person is watching."
                                else -> "$watchingCount people are watching."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Brand.OnDarkMuted,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Default.Warning, Brand.Amber, size = 52.dp)
                    Text(
                        if (attention.size == 1) "1 thing needs attention" else "${attention.size} things need attention",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Brand.OnDark,
                    )
                }
                for (item in attention) AttentionRow(item, onAdd, onOpenSettings)
            }
        }
    }
}

@Composable
private fun AttentionRow(item: Attention, onAdd: (UserStatus) -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val text = when (item) {
            Attention.NeedsKey -> "The Hall Monitor key is not set on this phone."
            is Attention.KeyRefused -> item.detail
            is Attention.Offline -> item.detail.ifEmpty { "Can't reach the server." } + " Retrying…"
            is Attention.AtLimit -> "${item.user.username} reached their ${item.user.periodLabel} limit."
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Brand.OnDark, modifier = Modifier.weight(1f))
        val colors = ButtonDefaults.buttonColors(containerColor = Brand.Mint, contentColor = Brand.Deep)
        when (item) {
            Attention.NeedsKey, is Attention.KeyRefused -> Button(onClick = onOpenSettings, colors = colors) { Text("Settings", fontWeight = FontWeight.Bold) }
            is Attention.AtLimit -> Button(onClick = { onAdd(item.user) }, colors = colors) { Text("+$QUICK_ADD min", fontWeight = FontWeight.Bold) }
            is Attention.Offline -> Unit
        }
    }
}

/** Someone who is playing right now: what, how long they have left, and the two actions a parent reaches for. */
@Composable
internal fun WatchingNowCard(
    card: UserCard,
    nowMs: Long,
    loadAvatar: suspend (String) -> android.graphics.Bitmap?,
    loadPoster: suspend (String) -> android.graphics.Bitmap?,
    onAdd: () -> Unit,
    onStop: () -> Unit,
) {
    val status = card.status
    val np = status.nowPlaying
    val remaining = card.remainingNow(nowMs)
    val ok = okColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(ok))
                Pill(if (np?.isPaused == true) "Paused" else "Watching now", ok)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (np != null) Poster(np.itemId, np.itemName, loadPoster)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(status.userId, status.username, loadAvatar)
                        Text(status.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (np != null) {
                        Text(np.itemName.ifEmpty { "Playing" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("${np.client.ifEmpty { "Unknown" }} · ${np.deviceName.ifEmpty { "Device" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (remaining < 0) "No time limit" else if (remaining <= 0) "No time left" else "${WatchText.durationLeft(remaining)} left",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (remaining in 0.0..300.0) warnColor() else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("+$QUICK_ADD min") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop stream") }
            }
        }
    }
}

/** A progress ring (the logo's rings, one per child) around [content]: the coloured arc is the time left. */
@Composable
internal fun TimeRing(fraction: Float, color: Color, content: @Composable () -> Unit) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(
        Modifier.size(62.dp).drawBehind {
            val stroke = 5.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = topLeft, size = arc, style = Stroke(stroke))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** One row per limited user: picture in a ring of time left, name and state, time left, this week's total. Tapping opens the full card. */
@Composable
internal fun KidsCard(cards: List<UserCard>, nowMs: Long, loadAvatar: suspend (String) -> android.graphics.Bitmap?, onOpen: () -> Unit) {
    val now = Instant.ofEpochMilli(nowMs)
    val ok = okColor()
    val warn = warnColor()
    val bad = MaterialTheme.colorScheme.error
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = HomeCardShape) {
        Column(Modifier.padding(vertical = 6.dp)) {
            cards.forEachIndexed { index, card ->
                val s = card.status
                val timeout = s.activeTimeOutEnd(now)
                val remaining = card.remainingNow(nowMs)
                val reason = s.block(now)
                val (state, stateColor) = when {
                    !s.enabled -> "Limit switched off" to quiet
                    timeout != null -> "Timed out until ${DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(timeout)}" to bad
                    reason == "TimeLimitExceeded" -> "Limit reached" to bad
                    reason == "OutsideTimeWindow" -> "Outside allowed hours" to warn
                    card.paused -> "Paused" to warn
                    card.playing -> "Watching now" to ok
                    else -> "Not watching" to quiet
                }
                val limited = s.enabled && remaining >= 0
                val allowed = s.allowedSeconds
                val fraction = if (!limited) 1f else if (allowed > 0) (remaining / allowed).toFloat() else 0f
                val ringColor = when {
                    !limited -> quiet.copy(alpha = 0.45f)
                    remaining <= 0 -> bad
                    remaining < 300 -> warn
                    else -> ok
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TimeRing(fraction, ringColor) { Avatar(s.userId, s.username, loadAvatar) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(s.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Pill(state, stateColor)
                        if (s.secondsWatchedWeekly > 0) {
                            Text("This week: ${WatchText.duration(s.secondsWatchedWeekly)}", style = MaterialTheme.typography.bodySmall, color = quiet)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (!limited) {
                            Text("No limit", style = MaterialTheme.typography.bodyMedium, color = quiet)
                        } else {
                            Text(
                                if (remaining <= 0) "0 min" else WatchText.durationLeft(remaining),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (remaining <= 0) bad else if (remaining < 300) warn else MaterialTheme.colorScheme.onSurface,
                            )
                            Text("left", style = MaterialTheme.typography.bodySmall, color = quiet)
                        }
                    }
                }
                if (index < cards.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
    }
}

/** Bedtime: stop everyone for a while. Always asks first (the dialog) and can be undone. */
@Composable
internal fun BedtimeCard(anyTimedOut: Boolean, onBedtime: () -> Unit, onEndTimeOuts: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heroSurface(HomeCardShape)
            .drawBehind {
                drawCircle(Brand.Violet.copy(alpha = 0.20f), radius = size.height * 0.8f, center = Offset(size.width * 0.92f, size.height * 0.05f))
            },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Bedtime, Brand.Violet, size = 44.dp)
                Text("Bedtime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Brand.OnDark)
            }
            Text(
                "Stops playback and blocks watching for the people you choose, for an hour or until the morning.",
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.OnDarkMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onBedtime,
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.Mint, contentColor = Brand.Deep),
                    modifier = Modifier.weight(1f),
                ) { Text("Bedtime…", fontWeight = FontWeight.Bold) }
                if (anyTimedOut) {
                    OutlinedButton(
                        onClick = onEndTimeOuts,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand.OnDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Brand.OnDarkMuted),
                        modifier = Modifier.weight(1f),
                    ) { Text("End time-outs") }
                }
            }
        }
    }
}

/** Icons for the section headings used by the screen. */
internal object HomeIcons {
    val Watching = Icons.Default.PlayCircle
    val Kids = Icons.Default.Group
}
