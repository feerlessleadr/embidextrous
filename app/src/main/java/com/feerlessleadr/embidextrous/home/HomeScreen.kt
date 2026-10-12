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
    val onOpenServer: (() -> Unit)? = null,
    val onOpenPlugins: (() -> Unit)? = null,
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
                            item(key = "watching") { SectionTitle("Watching now", HomeIcons.Watching) }
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
                            item(key = "kids-title") { SectionTitle("Kids today", HomeIcons.Kids) }
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
                        HomeCard.Server -> if (admin != null) item(key = "server") { ServerCard(admin.server, admin.onOpenServer) }
                        HomeCard.Plugins -> if (admin != null) item(key = "plugins") { PluginsCard(admin.server, admin.onOpenPlugins) }
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
