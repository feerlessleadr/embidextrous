package com.feerlessleadr.embidextrous.hallpass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.feerlessleadr.embidextrous.data.HpRow
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.ui.Pill
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How far (as a share of the screen width) a card must be dragged before letting go counts as a decision. */
internal const val SWIPE_FRACTION = 0.3f

/** Which way a drag ended: right is clean, left is explicit, anything short of the threshold is neither. Pure, so it is unit tested. */
internal fun swipeOutcome(offsetPx: Float, widthPx: Float): String? = when {
    widthPx <= 0f -> null
    offsetPx >= widthPx * SWIPE_FRACTION -> "clean"
    offsetPx <= -widthPx * SWIPE_FRACTION -> "explicit"
    else -> null
}

/**
 * The swipe review: a full-screen view that shows one track at a time and why Hall Pass was unsure. Swipe right for clean, left for explicit (the
 * buttons do the same), Skip leaves it for later, Undo takes the last decision back. It works through whatever the list behind it shows (the filters,
 * the reason, the search, the opened album or artist and the sort, so "most played first" is decided first).
 */
@Composable
internal fun SwipeDialog(vm: HallPassViewModel, state: HallPassState) {
    Dialog(onDismissRequest = vm::stopSwipe, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val row = state.rows.firstOrNull()
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Swipe review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${state.total} left · ${state.swiped} decided this time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = vm::stopSwipe) { Text("Done") }
                }
                if (row == null) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.loading) "Loading…" else "Nothing more to decide here.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SwipeCard(row, onDecide = { decision -> vm.swipeDecide(row, decision) })
                    }
                    Text(
                        "Swipe right = clean · swipe left = explicit",
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { row?.let { vm.swipeDecide(it, "explicit") } }, enabled = row != null, modifier = Modifier.weight(1f)) { Text("Explicit") }
                    OutlinedButton(onClick = { row?.let { vm.swipeSkip(it) } }, enabled = row != null, modifier = Modifier.weight(1f)) { Text("Skip") }
                    OutlinedButton(onClick = { row?.let { vm.swipeDecide(it, "clean") } }, enabled = row != null, modifier = Modifier.weight(1f)) { Text("Clean") }
                }
                TextButton(onClick = vm::swipeUndo, enabled = state.undoable > 0, modifier = Modifier.fillMaxWidth()) { Text("Undo the last one (${state.undoable})") }
            }
        }
    }
}

@Composable
private fun SwipeCard(row: HpRow, onDecide: (String) -> Unit) {
    val offsetX = remember(row.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val ok = okColor()
    val bad = MaterialTheme.colorScheme.error
    val progress = (offsetX.value / (widthPx * SWIPE_FRACTION)).coerceIn(-1f, 1f)
    Card(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = if (widthPx > 0f) offsetX.value / widthPx * 10f else 0f }
            .pointerInput(row.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val outcome = swipeOutcome(offsetX.value, widthPx)
                            if (outcome == null) {
                                offsetX.animateTo(0f, tween(150))
                            } else {
                                offsetX.animateTo(if (outcome == "clean") widthPx * 1.2f else -widthPx * 1.2f, tween(140))
                                onDecide(outcome)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(150)) } },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + delta) }
                    },
                )
            },
        shape = HomeCardShape,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(row.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOf(row.artist, row.album).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Unknown artist" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(decisionLabel(row.decision), MaterialTheme.colorScheme.primary)
                    if (row.plays > 0) Text("played ${row.plays} ${if (row.plays == 1) "time" else "times"}", style = MaterialTheme.typography.bodySmall)
                }
                if (row.reason.isNotEmpty()) Text(row.reason, style = MaterialTheme.typography.bodyMedium)
                Text("Why Hall Pass was unsure", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (row.evidence.isEmpty()) {
                    Text(if (row.skipped) "In a skipped genre." else "Nothing was found about this track.", style = MaterialTheme.typography.bodyMedium)
                }
                row.evidence.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (progress > 0.05f) {
                Text(
                    "CLEAN",
                    Modifier.align(Alignment.TopStart).padding(20.dp).graphicsLayer { alpha = progress },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = ok,
                )
            }
            if (progress < -0.05f) {
                Text(
                    "EXPLICIT",
                    Modifier.align(Alignment.TopEnd).padding(20.dp).graphicsLayer { alpha = -progress },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = bad,
                )
            }
        }
    }
}
