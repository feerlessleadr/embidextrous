package com.feerlessleadr.embidextrous.server

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.feerlessleadr.embidextrous.data.ActivityEntry
import com.feerlessleadr.embidextrous.data.ActivityKind
import com.feerlessleadr.embidextrous.data.DeviceInfo
import com.feerlessleadr.embidextrous.data.LiveSession
import com.feerlessleadr.embidextrous.data.PlayMethod
import com.feerlessleadr.embidextrous.data.ServerTask
import com.feerlessleadr.embidextrous.home.AdminHomeViewModel
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.PluginsCard
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.Avatar
import com.feerlessleadr.embidextrous.watching.ConfirmDialog
import com.feerlessleadr.embidextrous.watching.Poster
import kotlinx.coroutines.delay

private const val ACTIVE_WINDOW_MS = 10 * 60_000L

internal fun ago(epochMs: Long?, nowMs: Long): String = when {
    epochMs == null -> "never"
    nowMs - epochMs < 60_000L -> "just now" // also covers a server clock a little ahead of the phone
    else -> DateUtils.getRelativeTimeSpanString(epochMs, nowMs, DateUtils.MINUTE_IN_MILLIS).toString()
}

private fun clock(ticks: Long): String {
    val total = ticks / 10_000_000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** A screen of the Server group: pull to refresh, a snackbar for what an action did, and [content] inside. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolScreen(vm: ServerToolsViewModel, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) { content() }
    }
}

@Composable
internal fun EmptyNote(icon: ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 80.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

// ---------------------------------------------------------------------------------------------------------
// Live
// ---------------------------------------------------------------------------------------------------------

/** Who is connected right now and what they are playing; Emby pushes every change while this is on screen, and it asks for the list only if the push is down. */
@Composable
fun LiveScreen(vm: ServerToolsViewModel) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vm) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.keepLiveFresh() } }
    val state by vm.live.collectAsStateWithLifecycle()
    var stopping by remember { mutableStateOf<LiveSession?>(null) }
    var showOthers by rememberSaveable { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val playing = state.items.filter { it.playing != null }
    val connected = state.items.filter { it.playing == null && it.userName.isNotEmpty() && (it.lastActivityMs ?: 0L) > now - ACTIVE_WINDOW_MS }
    val others = state.items.filter { it !in playing && it !in connected }
    ToolScreen(vm, onRefresh = vm::loadLive) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error != null && state.items.isEmpty() -> EmptyNote(Icons.Default.Warning, "Could not load sessions", state.error.orEmpty())
            playing.isEmpty() && connected.isEmpty() && others.isEmpty() -> EmptyNote(Icons.Default.Person, "Nobody is connected", "Sessions appear here while someone has an app open.")
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                if (playing.isNotEmpty()) {
                    item { Heading("Playing now · ${playing.size}") }
                    items(playing, key = { "p-${it.id}" }) { PlayingCard(it, now, vm::avatar, vm::poster) { stopping = it } }
                } else {
                    item { Heading("Nobody is playing right now") }
                }
                if (connected.isNotEmpty()) {
                    item { Heading("Connected · ${connected.size}") }
                    items(connected, key = { "c-${it.id}" }) { ConnectedRow(it, now, vm::avatar) }
                }
                if (others.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showOthers = !showOthers }) { Text(if (showOthers) "Hide other devices" else "Show ${others.size} other devices (speakers, TVs, idle apps)") }
                    }
                    if (showOthers) items(others, key = { "o-${it.id}" }) { ConnectedRow(it, now, vm::avatar) }
                }
            }
        }
    }
    stopping?.let { session ->
        ConfirmDialog(
            title = "Stop this stream?",
            message = "This asks ${session.userName.ifEmpty { session.device }}'s app to stop \"${session.playing?.title.orEmpty()}\" now. They can start it again.",
            confirm = "Stop",
            onDismiss = { stopping = null },
            onConfirm = { stopping = null; vm.stopPlayback(session) },
        )
    }
}

@Composable
private fun PlayingCard(
    session: LiveSession,
    now: Long,
    loadAvatar: suspend (String) -> android.graphics.Bitmap?,
    loadPoster: suspend (String) -> android.graphics.Bitmap?,
    onStop: () -> Unit,
) {
    val p = session.playing ?: return
    val ok = okColor()
    val warn = warnColor()
    val (methodText, methodColor) = when (p.method) {
        PlayMethod.Direct -> "Direct play" to ok
        PlayMethod.DirectStream -> "Direct stream" to MaterialTheme.colorScheme.primary
        PlayMethod.Transcode -> "Transcoding" to warn
        PlayMethod.Unknown -> "Playing" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // The poster of what is playing; with none (a live channel, a song without art) the play badge stands in.
                if (p.posterId.isNotEmpty()) Poster(p.posterId, p.title, loadPoster) else IconBadge(Icons.Default.PlayArrow, methodColor, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.title.ifEmpty { "Playing" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    p.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Pill(if (p.paused) "Paused" else methodText, if (p.paused) warn else methodColor)
            }
            if (p.runTicks > 0) {
                LinearProgressIndicator(progress = { (p.positionTicks.toFloat() / p.runTicks).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${clock(p.positionTicks)} of ${clock(p.runTicks)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.method == PlayMethod.Transcode) {
                val why = listOfNotNull(p.codecs?.let { "to $it" }, p.reasons.takeIf { it.isNotEmpty() }?.joinToString(", ") { reason -> reason.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase() })
                if (why.isNotEmpty()) Text(why.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = warn)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (session.userId.isNotEmpty()) Avatar(session.userId, session.userName, loadAvatar)
                Column(Modifier.weight(1f)) {
                    Text(session.userName.ifEmpty { "Unknown user" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(session.client, session.device).filter { it.isNotEmpty() }.joinToString(" · ") + (if (session.remote.isNotEmpty()) " · ${session.remote}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Stop")
                }
            }
        }
    }
}

@Composable
private fun ConnectedRow(session: LiveSession, now: Long, loadAvatar: suspend (String) -> android.graphics.Bitmap?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (session.userId.isNotEmpty()) Avatar(session.userId, session.userName, loadAvatar) else IconBadge(Icons.Default.Devices, MaterialTheme.colorScheme.primary, size = 38.dp)
        Column(Modifier.weight(1f)) {
            Text(session.userName.ifEmpty { session.client.ifEmpty { "Device" } }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(session.client, session.device).filter { it.isNotEmpty() }.distinct().joinToString(" · ") + " · " + ago(session.lastActivityMs, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------------------------------------

/** The server's scheduled tasks: what is running, what failed, and when everything last ran. */
@Composable
fun TasksScreen(vm: ServerToolsViewModel) {
    val owner = LocalLifecycleOwner.current
    val state by vm.tasks.collectAsStateWithLifecycle()
    // Emby pushes the tasks as they change (a running task's progress moves by itself); it is asked only while the push is down.
    LaunchedEffect(vm) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.keepTasksFresh() } }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var asking by remember { mutableStateOf<ServerTask?>(null) }
    val shown = state.items.filter { showHidden || !it.hidden || it.running || it.lastStatus == "Failed" }
        .sortedWith(compareBy({ !it.running }, { it.lastStatus != "Failed" }, { it.category }, { it.name.lowercase() }))
    ToolScreen(vm, onRefresh = vm::loadTasks) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error != null && state.items.isEmpty() -> EmptyNote(Icons.Default.Warning, "Could not load tasks", state.error.orEmpty())
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                item {
                    FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text("Show system tasks (${state.items.count { it.hidden }})") })
                }
                items(shown, key = { it.id }) { task -> TaskCard(task, System.currentTimeMillis()) { asking = task } }
            }
        }
    }
    asking?.let { task ->
        ConfirmDialog(
            title = if (task.running) "Stop ${task.name}?" else "Run ${task.name} now?",
            message = if (task.running) "It stops at the next safe point." else task.description.ifEmpty { "It runs once now, as well as on its schedule." },
            confirm = if (task.running) "Stop" else "Run now",
            onDismiss = { asking = null },
            onConfirm = { asking = null; if (task.running) vm.stopTask(task) else vm.runTask(task) },
        )
    }
}

@Composable
private fun TaskCard(task: ServerTask, now: Long, onAction: () -> Unit) {
    val ok = okColor()
    val warn = warnColor()
    val failed = task.lastStatus == "Failed"
    val (pillText, pillColor) = when {
        task.running -> "Running" to MaterialTheme.colorScheme.primary
        failed -> "Failed" to warn
        task.lastStatus == "Completed" -> "Completed" to ok
        task.lastStatus == null -> "Never run" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> (task.lastStatus) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Schedule, pillColor, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(task.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(task.category.takeIf { it.isNotEmpty() }, task.lastEndedMs?.let { "last ran " + ago(it, now) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(pillText, pillColor)
                IconButton(onClick = onAction) {
                    Icon(if (task.running) Icons.Default.Cancel else Icons.Default.PlayArrow, contentDescription = if (task.running) "Stop ${task.name}" else "Run ${task.name} now")
                }
            }
            if (task.running) {
                val progress = task.progress
                if (progress != null) LinearProgressIndicator(progress = { (progress / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (failed && !task.lastError.isNullOrBlank()) Text(task.lastError, style = MaterialTheme.typography.bodySmall, color = warn)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------------------------------------

private enum class ActivityFilter(val label: String) { All("All"), Problems("Problems"), SignIns("Sign-ins"), Playback("Playback") }

/** The server's recent log: sign-ins (and failed ones), playback, plugin and user changes, warnings. */
@Composable
fun ActivityScreen(vm: ServerToolsViewModel) {
    val owner = LocalLifecycleOwner.current
    // New entries are pushed and appear on top by themselves; the log is asked for again only while the push is down.
    LaunchedEffect(vm) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.keepActivityFresh() } }
    val state by vm.activity.collectAsStateWithLifecycle()
    val hasMore by vm.activityHasMore.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.All) }
    val shown = state.items.filter {
        when (filter) {
            ActivityFilter.All -> true
            ActivityFilter.Problems -> it.isProblem
            ActivityFilter.SignIns -> it.kind == ActivityKind.SignIn || it.kind == ActivityKind.SignInFailed
            ActivityFilter.Playback -> it.kind == ActivityKind.Playback
        }
    }
    ToolScreen(vm, onRefresh = { vm.loadActivity() }) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error != null && state.items.isEmpty() -> EmptyNote(Icons.Default.Warning, "Could not load the activity log", state.error.orEmpty())
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityFilter.entries.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) }) }
                    }
                }
                if (shown.isEmpty()) item { Text("Nothing matches in the entries loaded so far.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                items(shown, key = { it.id }) { ActivityRow(it, System.currentTimeMillis()) }
                if (hasMore) item { TextButton(onClick = { vm.loadActivity(more = true) }, modifier = Modifier.fillMaxWidth()) { Text("Load older entries") } }
            }
        }
    }
}

@Composable
private fun ActivityRow(entry: ActivityEntry, now: Long) {
    val warn = warnColor()
    val (icon, tint) = when (entry.kind) {
        ActivityKind.SignIn -> Icons.AutoMirrored.Filled.Login to MaterialTheme.colorScheme.primary
        ActivityKind.SignInFailed -> Icons.Default.Warning to warn
        ActivityKind.Playback -> Icons.Default.PlayCircle to MaterialTheme.colorScheme.primary
        ActivityKind.Plugin -> Icons.Default.Extension to MaterialTheme.colorScheme.primary
        ActivityKind.User -> Icons.Default.Person to MaterialTheme.colorScheme.primary
        ActivityKind.Other -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color = if (entry.isProblem) warn else tint
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, color, size = 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            entry.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Text(ago(entry.dateMs, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Devices
// ---------------------------------------------------------------------------------------------------------

/** Every app and browser that has signed in, with the option to sign one out for good. */
@Composable
fun DevicesScreen(vm: ServerToolsViewModel, thisDeviceId: String) {
    val owner = LocalLifecycleOwner.current
    // A device signing in is pushed (as an activity entry) and is the cue to look again; asked for every minute only while the push is down.
    LaunchedEffect(vm) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.keepDevicesFresh() } }
    val state by vm.devices.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<DeviceInfo?>(null) }
    var renaming by remember { mutableStateOf<DeviceInfo?>(null) }
    ToolScreen(vm, onRefresh = vm::loadDevices) {
        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error != null && state.items.isEmpty() -> EmptyNote(Icons.Default.Warning, "Could not load devices", state.error.orEmpty())
            state.items.isEmpty() -> EmptyNote(Icons.Default.Devices, "No devices", "Apps and browsers show up here after they sign in.")
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                item { Heading("${state.items.size} devices, most recent first · tap one to rename it") }
                items(state.items, key = { it.id }) { device ->
                    DeviceCard(device, mine = device.reportedId == thisDeviceId, now = System.currentTimeMillis(), onRename = { renaming = device }) { asking = device }
                }
            }
        }
    }
    renaming?.let { device ->
        RenameDeviceDialog(
            device = device,
            loadCustomName = { vm.customName(device) },
            onDismiss = { renaming = null },
            onSave = { name -> renaming = null; vm.rename(device, name) },
        )
    }
    asking?.let { device ->
        val mine = device.reportedId == thisDeviceId
        ConfirmDialog(
            title = "Sign ${device.name} out?",
            message = if (mine) "This is the phone you are using. It will be signed out of the server and the app will ask you to sign in again."
            else "${device.userName.ifEmpty { "Whoever uses it" }} will have to sign in again on that device.",
            confirm = "Sign out",
            onDismiss = { asking = null },
            onConfirm = { asking = null; vm.revoke(device) },
        )
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, mine: Boolean, now: Long, onRename: () -> Unit, onRevoke: () -> Unit) {
    val ok = okColor()
    val activeNow = (device.lastActivityMs ?: 0L) > now - 5 * 60_000L
    Card(Modifier.fillMaxWidth().clip(HomeCardShape).clickable(onClick = onRename), shape = HomeCardShape) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Default.Devices, if (activeNow) ok else MaterialTheme.colorScheme.primary, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name.ifEmpty { "Device" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (mine) Pill("This phone", MaterialTheme.colorScheme.primary)
                }
                Text(
                    listOf(device.appName, device.appVersion).filter { it.isNotEmpty() }.joinToString(" ") + if (device.userName.isNotEmpty()) " · ${device.userName}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    (if (activeNow) "Active now" else "Last active " + ago(device.lastActivityMs, now)) + if (device.ip.isNotEmpty()) " · ${device.ip}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (activeNow) ok else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onRevoke) { Text("Sign out") }
        }
    }
}

/** The longest name Emby is asked to keep for a device. */
private const val MAX_DEVICE_NAME = 80

/** Gives a device a name of your own, the same thing Emby's dashboard does; Use the original goes back to the name the device reports. */
@Composable
private fun RenameDeviceDialog(device: DeviceInfo, loadCustomName: suspend () -> String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    // The name set earlier, if any (the list already shows it), so the box starts with what is there now.
    val custom by produceState<String?>(null, device.id) { value = loadCustomName() }
    var text by remember(device.id) { mutableStateOf(device.name) }
    var touched by remember(device.id) { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOf(device.appName, device.appVersion).filter { it.isNotEmpty() }.joinToString(" ") + (if (device.userName.isNotEmpty()) " · ${device.userName}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_DEVICE_NAME) { text = it; touched = true } },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Shown here and in Emby instead of the name the device reports.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank() && touched) { Text("Save") } },
        dismissButton = {
            Row {
                if (custom != null) TextButton(onClick = { onSave("") }) { Text("Use the original") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------------------
// Plugins
// ---------------------------------------------------------------------------------------------------------

/** The plugin overview now; the plugin manager (checking, updating, adding and removing GitHub plugins) is built on top of this later. */
@Composable
fun PluginsTab(adminVm: AdminHomeViewModel) {
    LaunchedEffect(adminVm) { adminVm.load() }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
        item { PluginsCard(adminVm) }
        item {
            Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plugin manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Checking for new versions, updating, restoring, adding and removing plugins installed from GitHub will live here, using the GitHub Repo Plugin Install plugin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Pill("Coming soon", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
