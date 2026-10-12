package com.feerlessleadr.embidextrous.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.HomeCard
import com.feerlessleadr.embidextrous.data.HomeLayout
import com.feerlessleadr.embidextrous.data.UserCard
import com.feerlessleadr.embidextrous.data.UserStatus
import com.feerlessleadr.embidextrous.data.WatchText
import com.feerlessleadr.embidextrous.watching.Attention
import com.feerlessleadr.embidextrous.watching.Avatar
import com.feerlessleadr.embidextrous.watching.Banner
import com.feerlessleadr.embidextrous.watching.Bedtime
import com.feerlessleadr.embidextrous.watching.ConfirmDialog
import com.feerlessleadr.embidextrous.watching.HomeModel
import com.feerlessleadr.embidextrous.watching.Link
import com.feerlessleadr.embidextrous.watching.Poster
import com.feerlessleadr.embidextrous.watching.WatchingEyeTheme
import com.feerlessleadr.embidextrous.watching.WatchingEyeViewModel
import com.feerlessleadr.embidextrous.ui.ListsViewModel
import kotlinx.coroutines.flow.StateFlow
import com.feerlessleadr.embidextrous.watching.subtitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val QUICK_ADD_MINUTES = 15

/**
 * The start screen: is everything fine, and if not, what needs the person? Then who is watching what, each child's time left,
 * and Bedtime. A summary only: the Hall Monitor tab keeps the full cards and every action.
 */
/** The administrator cards of Home: the smart lists (its own view model, shared with the Smart Lists tab), the server and the plugins. Null on a phone that does not show them. */
class AdminHome(
    val lists: ListsViewModel,
    val server: AdminHomeViewModel,
    val problem: StateFlow<String?>,
    val onOpenLists: () -> Unit,
)

@Composable
fun HomeScreen(
    vm: WatchingEyeViewModel,
    admin: AdminHome?,
    layout: HomeLayout,
    onCustomise: () -> Unit,
    onOpenWatching: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    WatchingEyeTheme { HomeContent(vm, admin, layout, onCustomise, onOpenWatching, onOpenSettings) }
}

private sealed interface HomeDialog {
    data class Stop(val user: UserStatus, val title: String) : HomeDialog
    data object Bedtime : HomeDialog
    data object EndTimeOuts : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    vm: WatchingEyeViewModel,
    admin: AdminHome?,
    layout: HomeLayout,
    onCustomise: () -> Unit,
    onOpenWatching: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }

    // Live data only flows while a screen that needs it is visible.
    LifecycleStartEffect(vm) {
        vm.acquire()
        onStopOrDispose { vm.release() }
    }
    // The server cards look again when Home comes to the front (not more often than every 30 seconds) and on pull to refresh.
    LifecycleStartEffect(admin) {
        admin?.server?.load()
        onStopOrDispose { }
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            val result = snackbar.showSnackbar(
                message = event.message,
                actionLabel = event.undoLabel,
                withDismissAction = event.isError,
                duration = if (event.undo != null || event.isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) event.undo?.let { undo -> scope.launch { undo() } }
        }
    }
    // "Refresh all" and the like report through the smart lists' own messages.
    LaunchedEffect(admin) {
        admin?.lists?.messages?.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Short) }
    }
    // One clock for the whole screen, so the countdowns move between updates.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val instant = Instant.ofEpochMilli(now)
    val attention = HomeModel.attention(state.cards, state.link, state.detail, instant)
    val watching = HomeModel.watchingNow(state.cards)
    val timedOut = HomeModel.timedOut(state.cards, instant)
    val candidates = HomeModel.bedtimeCandidates(state.cards)
    val cardsToShow = layout.visible(HomeCard.availableFor(administrator = admin != null))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Home")
                        Text(subtitle(state, now), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onCustomise) { Icon(Icons.Default.Tune, contentDescription = "Customise Home") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = {
                vm.refresh()
                admin?.server?.load(force = true)
                admin?.lists?.reload()
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(12.dp)) {
                item(key = "status") {
                    StatusCard(
                        attention = attention,
                        watchingCount = watching.size,
                        loaded = state.loaded,
                        onAdd = { user -> vm.extend(user, QUICK_ADD_MINUTES) },
                        onOpenSettings = onOpenSettings,
                    )
                }
                for (card in cardsToShow) {
                    when (card) {
                        HomeCard.WatchingNow -> if (watching.isNotEmpty()) {
                            item(key = "watching") { SectionTitle("Watching now") }
                            for (c in watching) {
                                item(key = "watch-${c.status.userId}") {
                                    WatchingNowCard(
                                        card = c,
                                        nowMs = now,
                                        loadAvatar = vm::avatar,
                                        loadPoster = vm::poster,
                                        onAdd = { vm.extend(c.status, QUICK_ADD_MINUTES) },
                                        onStop = { dialog = HomeDialog.Stop(c.status, c.status.nowPlaying?.itemName.orEmpty()) },
                                    )
                                }
                            }
                        }
                        HomeCard.Kids -> if (state.loaded && state.cards.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    "No limited users yet. Add users on the Hall Monitor plugin page in the Emby dashboard.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        } else if (state.cards.isNotEmpty()) {
                            item(key = "kids-title") { SectionTitle("Kids today") }
                            item(key = "kids") { KidsCard(state.cards, now, vm::avatar, onOpenWatching) }
                        }
                        HomeCard.Bedtime -> if (candidates.isNotEmpty()) {
                            item(key = "bedtime") {
                                BedtimeCard(
                                    anyTimedOut = timedOut.isNotEmpty(),
                                    onBedtime = { dialog = HomeDialog.Bedtime },
                                    onEndTimeOuts = { dialog = HomeDialog.EndTimeOuts },
                                )
                            }
                        }
                        HomeCard.SmartLists -> if (admin != null) item(key = "smart-lists") { SmartListsCard(admin, now) }
                        HomeCard.Server -> if (admin != null) item(key = "server") { ServerCard(admin.server) }
                        HomeCard.Plugins -> if (admin != null) item(key = "plugins") { PluginsCard(admin.server) }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is HomeDialog.Stop -> ConfirmDialog(
            title = "Stop ${d.user.username}'s stream?",
            message = "This stops \"${d.title.ifEmpty { "the stream" }}\" now. It does not block them from starting it again.",
            confirm = "Stop",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.stopStream(d.user) },
        )
        HomeDialog.Bedtime -> BedtimeDialog(
            candidates = candidates,
            onDismiss = { dialog = null },
            onStart = { users, minutes -> dialog = null; vm.bedtime(users, minutes) },
        )
        HomeDialog.EndTimeOuts -> ConfirmDialog(
            title = "End all time-outs?",
            message = "${timedOut.joinToString(", ") { it.status.username }} can watch again right away.",
            confirm = "End time-outs",
            onDismiss = { dialog = null },
            onConfirm = { dialog = null; vm.endAllTimeOuts(timedOut.map { it.status }) },
        )
        null -> Unit
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

/** "All good", or what needs attention, each with the one action that helps. */
@Composable
private fun StatusCard(attention: List<Attention>, watchingCount: Int, loaded: Boolean, onAdd: (UserStatus) -> Unit, onOpenSettings: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ok = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val warn = if (dark) Color(0xFFFFB74D) else Color(0xFF9A5B00)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (attention.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ok, modifier = Modifier.size(28.dp))
                    Column {
                        Text(if (loaded) "All good" else "Checking…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                !loaded -> "Loading the latest from the server."
                                watchingCount == 0 -> "Nobody is watching right now."
                                watchingCount == 1 -> "One person is watching."
                                else -> "$watchingCount people are watching."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = warn, modifier = Modifier.size(28.dp))
                    Text(
                        if (attention.size == 1) "1 thing needs attention" else "${attention.size} things need attention",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                for (item in attention) {
                    AttentionRow(item, onAdd, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(item: Attention, onAdd: (UserStatus) -> Unit, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        val text = when (item) {
            Attention.NeedsKey -> "The Hall Monitor key is not set on this phone."
            is Attention.KeyRefused -> item.detail
            is Attention.Offline -> item.detail.ifEmpty { "Can't reach the server." } + " Retrying…"
            is Attention.AtLimit -> "${item.user.username} reached their ${item.user.periodLabel} limit."
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        when (item) {
            Attention.NeedsKey, is Attention.KeyRefused -> FilledTonalButton(onClick = onOpenSettings) { Text("Settings") }
            is Attention.AtLimit -> FilledTonalButton(onClick = { onAdd(item.user) }) { Text("+$QUICK_ADD_MINUTES min") }
            is Attention.Offline -> Unit
        }
    }
}

/** Someone who is playing right now: what, how long they have left, and the two actions a parent reaches for. */
@Composable
private fun WatchingNowCard(
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (np != null) Poster(np.itemId, np.itemName, loadPoster)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(status.userId, status.username, loadAvatar)
                        Text(status.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (np != null) {
                        Text(np.itemName.ifEmpty { "Playing" } + if (np.isPaused) " (paused)" else "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("${np.client.ifEmpty { "Unknown" }} · ${np.deviceName.ifEmpty { "Device" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (remaining < 0) "No time limit" else if (remaining <= 0) "No time left" else "${WatchText.durationLeft(remaining)} left",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("+$QUICK_ADD_MINUTES min") }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop stream") }
            }
        }
    }
}

/** One compact row per limited user: picture, name and state, time left, this week's total. Tapping opens the full card. */
@Composable
private fun KidsCard(cards: List<UserCard>, nowMs: Long, loadAvatar: suspend (String) -> android.graphics.Bitmap?, onOpen: () -> Unit) {
    val now = Instant.ofEpochMilli(nowMs)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ok = if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
    val warn = if (dark) Color(0xFFFFB74D) else Color(0xFF9A5B00)
    val bad = MaterialTheme.colorScheme.error
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            cards.forEachIndexed { index, card ->
                val s = card.status
                val timeout = s.activeTimeOutEnd(now)
                val remaining = card.remainingNow(nowMs)
                val reason = s.block(now)
                val (state, color) = when {
                    !s.enabled -> "Limit switched off" to MaterialTheme.colorScheme.onSurfaceVariant
                    timeout != null -> "Timed out until ${DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(timeout)}" to bad
                    reason == "TimeLimitExceeded" -> "Limit reached" to bad
                    reason == "OutsideTimeWindow" -> "Outside allowed hours" to warn
                    card.paused -> "Paused" to warn
                    card.playing -> "Watching now" to ok
                    else -> "Not watching" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(s.userId, s.username, loadAvatar)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(state, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
                        if (s.secondsWatchedWeekly > 0) {
                            Text("This week: ${WatchText.duration(s.secondsWatchedWeekly)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        when {
                            !s.enabled || remaining < 0 -> Text("No limit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> {
                                Text(
                                    if (remaining <= 0) "0 min" else WatchText.durationLeft(remaining),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remaining <= 0) bad else if (remaining < 300) warn else MaterialTheme.colorScheme.onSurface,
                                )
                                Text("left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (s.enabled && remaining >= 0) {
                    val allowed = s.allowedSeconds
                    val used = maxOf(0.0, allowed - remaining)
                    LinearProgressIndicator(
                        progress = { if (allowed > 0) (used / allowed).toFloat().coerceIn(0f, 1f) else 1f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                if (index < cards.lastIndex) Box(Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Bedtime: stop everyone for a while. Always asks first (the dialog) and can be undone. */
@Composable
private fun BedtimeCard(anyTimedOut: Boolean, onBedtime: () -> Unit, onEndTimeOuts: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bedtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Stops playback and blocks watching for the people you choose, for an hour or until the morning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onBedtime, modifier = Modifier.weight(1f)) { Text("Bedtime…") }
                if (anyTimedOut) OutlinedButton(onClick = onEndTimeOuts, modifier = Modifier.weight(1f)) { Text("End time-outs") }
            }
        }
    }
}

private enum class BedtimeChoice { OneHour, UntilMorning }

@Composable
private fun BedtimeDialog(candidates: List<UserStatus>, onDismiss: () -> Unit, onStart: (List<UserStatus>, Int) -> Unit) {
    val opened = remember { ZonedDateTime.now() }
    var selected by remember { mutableStateOf(candidates.map { it.userId }.toSet()) }
    var choice by remember { mutableStateOf(BedtimeChoice.OneHour) }
    val untilMorning = Bedtime.minutesUntilNext(Bedtime.WAKE_HOUR, opened)
    val minutes = if (choice == BedtimeChoice.OneHour) Bedtime.ONE_HOUR_MINUTES else untilMorning
    val ends = Bedtime.endsAt(minutes, opened)
    val endsText = DateTimeFormatter.ofPattern("EEE").format(ends) + " " + DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(ends)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bedtime") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Who?", style = MaterialTheme.typography.titleSmall)
                for (user in candidates) {
                    Row(Modifier.fillMaxWidth().clickable { selected = if (user.userId in selected) selected - user.userId else selected + user.userId }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = user.userId in selected, onCheckedChange = { selected = if (it) selected + user.userId else selected - user.userId })
                        Text(user.username, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text("For how long?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().clickable { choice = BedtimeChoice.OneHour }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = choice == BedtimeChoice.OneHour, onClick = { choice = BedtimeChoice.OneHour })
                    Text("1 hour", style = MaterialTheme.typography.bodyLarge)
                }
                Row(Modifier.fillMaxWidth().clickable { choice = BedtimeChoice.UntilMorning }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = choice == BedtimeChoice.UntilMorning, onClick = { choice = BedtimeChoice.UntilMorning })
                    Text("Until 6:00 AM", style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "Playback stops right away and stays blocked until $endsText.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(candidates.filter { it.userId in selected }, minutes) }, enabled = selected.isNotEmpty()) { Text("Start bedtime") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
