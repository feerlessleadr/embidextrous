package com.feerlessleadr.embidextrous.watching

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.MAX_MINUTES
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val QUICK_MINUTES = listOf(15, 30, 60)
private const val RESUME_MINUTES = 30

private sealed interface WatchDialog {
    data class AddOther(val user: UserStatus) : WatchDialog
    data class TimeOut(val user: UserStatus) : WatchDialog
    data class Reset(val user: UserStatus) : WatchDialog
    data class Stop(val user: UserStatus, val title: String) : WatchDialog
}

@Composable
fun WatchingEyeScreen(vm: WatchingEyeViewModel) {
    WatchingEyeTheme { WatchingEyeContent(vm) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchingEyeContent(vm: WatchingEyeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<WatchDialog?>(null) }

    // Live data only flows while the screen is visible.
    LifecycleStartEffect(vm) {
        vm.acquire()
        onStopOrDispose { vm.release() }
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val result = snackbar.showSnackbar(
                message = event.message,
                actionLabel = event.undoLabel,
                withDismissAction = event.isError,
                duration = if (event.undo != null) SnackbarDuration.Long else if (event.isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) event.undo?.let { undo -> scope.launch { undo() } }
        }
    }

    // One clock for every card: the countdowns and positions move between updates without asking the server.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Watching Eye")
                    Text(subtitle(state, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.link == Link.NEEDS_KEY) {
                KeyPrompt(onSave = vm::savePluginKey)
            } else {
                Column(Modifier.fillMaxSize()) {
                    ConnectionBanner(state)
                    PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                        if (state.loaded && state.cards.isEmpty()) {
                            EmptyHint()
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                                items(state.cards, key = { it.status.userId }) { card ->
                                    UserCardView(
                                        card = card,
                                        nowMs = now,
                                        loadAvatar = vm::avatar,
                                        loadPoster = vm::poster,
                                        onAdd = { minutes -> vm.extend(card.status, minutes) },
                                        onAddOther = { dialog = WatchDialog.AddOther(card.status) },
                                        onTimeOut = { dialog = WatchDialog.TimeOut(card.status) },
                                        onEndTimeOut = { vm.clearTimeOut(card.status) },
                                        onResume = { vm.resume(card.status, RESUME_MINUTES) },
                                        onReset = { dialog = WatchDialog.Reset(card.status) },
                                        onStop = { dialog = WatchDialog.Stop(card.status, card.status.nowPlaying?.itemName.orEmpty()) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is WatchDialog.AddOther -> MinutesDialog(
            title = "Add time for ${d.user.username}",
            message = "How many minutes should they get?",
            initial = 45,
            confirm = "Add",
            onDismiss = { dialog = null },
            onChosen = { minutes -> dialog = null; vm.extend(d.user, minutes) },
        )
        is WatchDialog.TimeOut -> MinutesDialog(
            title = "Time out ${d.user.username}",
            message = "Playback stops right away and is blocked for this many minutes.",
            initial = 15,
            confirm = "Time out",
            onDismiss = { dialog = null },
            onChosen = { minutes -> dialog = null; vm.timeOut(d.user, minutes) },
        )
        is WatchDialog.Reset -> ConfirmDialog(
            title = "Reset ${d.user.username}'s time?",
            message = "The time used (day, week, month and year) and any extra time go back to zero.",
            confirm = "Reset",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.resetTime(d.user) },
        )
        is WatchDialog.Stop -> ConfirmDialog(
            title = "Stop ${d.user.username}'s stream?",
            message = "This stops \"${d.title.ifEmpty { "the stream" }}\" now. It does not block them from starting it again.",
            confirm = "Stop",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.stopStream(d.user) },
        )
        null -> Unit
    }
}

private fun subtitle(state: WatchState, nowMs: Long): String = when (state.link) {
    Link.LIVE -> "● Live"
    Link.CONNECTING -> "Connecting…"
    Link.NEEDS_KEY -> "Needs the Watching Eye key"
    Link.KEY_REFUSED -> "Key refused by the plugin"
    else -> if (state.lastDataMs <= 0) "No data yet" else {
        val seconds = maxOf(0, (nowMs - state.lastDataMs) / 1000)
        "Updated " + (if (seconds < 60) "$seconds s" else "${seconds / 60} min") + " ago"
    }
}

@Composable
private fun ConnectionBanner(state: WatchState) {
    when (state.link) {
        Link.POLLING -> Banner("Live updates are not connected, so the app checks every 20 seconds. ${state.detail}", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Link.OFFLINE -> Banner((state.detail.ifEmpty { "Can't reach the server." }) + " Retrying…", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        Link.KEY_REFUSED -> Banner(state.detail, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Unit
    }
}

@Composable
private fun Banner(text: String, background: Color, content: Color) {
    Surface(color = background, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No limited users yet. Add users on the Watching Eye plugin page in the Emby dashboard.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun KeyPrompt(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter the Watching Eye key", style = MaterialTheme.typography.titleLarge)
        Text(
            "On the plugin page in the Emby dashboard, open the External API tab, tick Enable Public API, generate a key and save. " +
                "Then paste the key here. It is stored encrypted on this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Watching Eye key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("Save") }
    }
}

@Composable
private fun UserCardView(
    card: UserCard,
    nowMs: Long,
    loadAvatar: suspend (String) -> Bitmap?,
    loadPoster: suspend (String) -> Bitmap?,
    onAdd: (Int) -> Unit,
    onAddOther: () -> Unit,
    onTimeOut: () -> Unit,
    onEndTimeOut: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onStop: () -> Unit,
) {
    val status = card.status
    val now = Instant.ofEpochMilli(nowMs)
    val timeoutEnd = status.activeTimeOutEnd(now)
    val reason = status.block(now)
    val remaining = card.remainingNow(nowMs)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ok = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val warn = if (dark) Color(0xFFFFB74D) else Color(0xFF9A5B00)
    val bad = MaterialTheme.colorScheme.error

    val (chipText, chipColor) = when {
        !status.enabled -> "Limit switched off" to MaterialTheme.colorScheme.onSurfaceVariant
        timeoutEnd != null -> "Timed out until ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(timeoutEnd)}" to bad
        reason == "TimeLimitExceeded" -> "Limit reached (${status.periodLabel})" to bad
        reason == "OutsideTimeWindow" -> "Outside allowed hours" to warn
        card.paused -> "Paused" to warn
        card.playing -> "Watching now" to ok
        else -> "Not watching" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(status.userId, status.username, loadAvatar)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(status.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(chipText, style = MaterialTheme.typography.bodyMedium, color = chipColor, fontWeight = FontWeight.Bold)
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More actions") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Time out…") }, onClick = { menuOpen = false; onTimeOut() })
                        if (timeoutEnd != null) DropdownMenuItem(text = { Text("End time-out") }, onClick = { menuOpen = false; onEndTimeOut() })
                        DropdownMenuItem(text = { Text("Reset time used…") }, onClick = { menuOpen = false; onReset() })
                        if (card.playing) DropdownMenuItem(text = { Text("Stop stream…") }, onClick = { menuOpen = false; onStop() })
                    }
                }
            }

            // Time left and the bar under it.
            when {
                !status.enabled -> {
                    Text("Limit is off", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Switched off on the plugin page.", style = MaterialTheme.typography.bodySmall)
                }
                remaining < 0 -> {
                    Text("No time limit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Watched today: ${WatchText.duration(status.secondsWatchedDaily)}", style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    val allowed = status.allowedSeconds
                    val used = maxOf(0.0, allowed - remaining)
                    Text(
                        if (remaining <= 0) "No time left" else "${WatchText.duration(remaining)} left",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (remaining <= 0) bad else if (remaining < 300) warn else MaterialTheme.colorScheme.onSurface,
                    )
                    LinearProgressIndicator(progress = { if (allowed > 0) (used / allowed).toFloat().coerceIn(0f, 1f) else 1f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Used ${WatchText.duration(used)} of ${WatchText.duration(allowed)} ${WatchText.periodPhrase(status.periodLabel)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // What they are watching.
            status.nowPlaying?.let { np ->
                val position = card.positionTicksNow(nowMs).let { if (np.runTimeTicks > 0) minOf(it, np.runTimeTicks) else it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(np.itemId, np.itemName, loadPoster)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(np.itemName.ifEmpty { "Playing" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            WatchText.clock(position) + (if (np.runTimeTicks > 0) " / " + WatchText.clock(np.runTimeTicks) else "") + (if (np.isPaused) "  (paused)" else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (np.runTimeTicks > 0) LinearProgressIndicator(progress = { (position.toFloat() / np.runTimeTicks).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${np.client.ifEmpty { "Unknown" }} · ${np.deviceName.ifEmpty { "Device" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (status.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    QUICK_MINUTES.forEach { minutes ->
                        FilledTonalButton(onClick = { onAdd(minutes) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("+$minutes") }
                    }
                    OutlinedButton(onClick = onAddOther, modifier = Modifier.weight(1.4f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Other…") }
                }
            }
            if (timeoutEnd != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onResume, modifier = Modifier.weight(1f)) { Text("End time-out + $RESUME_MINUTES") }
                    OutlinedButton(onClick = onEndTimeOut, modifier = Modifier.weight(1f)) { Text("End time-out") }
                }
            }
        }
    }
}

/** The user's profile picture in a circle; the first letter of the name stands in until it is loaded or when there is none. */
@Composable
private fun Avatar(userId: String, name: String, load: suspend (String) -> Bitmap?) {
    val picture by produceState<Bitmap?>(null, userId) { value = load(userId) }
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
        val bitmap = picture
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Profile picture of $name", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** The poster of what is being watched; nothing is drawn (and no space taken) when the item has no picture. */
@Composable
private fun Poster(itemId: String, name: String, load: suspend (String) -> Bitmap?) {
    if (itemId.isEmpty()) return
    val picture by produceState<Bitmap?>(null, itemId) { value = load(itemId) }
    val bitmap = picture ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Poster of $name",
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(width = 56.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)),
    )
}

/** A small dialog asking for a number of minutes (1 to 43200). */
@Composable
private fun MinutesDialog(title: String, message: String, initial: Int, confirm: String, onDismiss: () -> Unit, onChosen: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val minutes = parseMinutes(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    isError = minutes == null,
                    supportingText = { if (minutes == null) Text("Enter 1 to $MAX_MINUTES") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { minutes?.let(onChosen) }, enabled = minutes != null) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Minutes from typed text: 1..43200, or null when it is not a usable number. */
internal fun parseMinutes(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }
