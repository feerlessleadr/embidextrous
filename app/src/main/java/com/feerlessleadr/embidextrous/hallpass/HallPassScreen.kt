package com.feerlessleadr.embidextrous.hallpass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feerlessleadr.embidextrous.data.HpAlbum
import com.feerlessleadr.embidextrous.data.HpArtist
import com.feerlessleadr.embidextrous.data.HpGate
import com.feerlessleadr.embidextrous.data.HpPreview
import com.feerlessleadr.embidextrous.data.HpRow
import com.feerlessleadr.embidextrous.data.HpStatus
import com.feerlessleadr.embidextrous.data.HallPass
import com.feerlessleadr.embidextrous.home.HomeCardShape
import com.feerlessleadr.embidextrous.home.okColor
import com.feerlessleadr.embidextrous.home.warnColor
import com.feerlessleadr.embidextrous.ui.IconBadge
import com.feerlessleadr.embidextrous.ui.Pill
import com.feerlessleadr.embidextrous.watching.ConfirmDialog

/** Words for the plugin's decisions. */
internal fun decisionLabel(decision: String): String = when (decision) {
    "Explicit" -> "Explicit"
    "Clean" -> "Clean"
    "LikelyClean" -> "Likely clean"
    "Review" -> "Needs review"
    "Skipped" -> "Skipped"
    "Unscanned" -> "Not scanned"
    else -> decision
}

@Composable
private fun decisionColor(decision: String): Color = when (decision) {
    "Explicit" -> MaterialTheme.colorScheme.error
    "Clean" -> okColor()
    "LikelyClean" -> MaterialTheme.colorScheme.primary
    "Review" -> warnColor()
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The Library group's Hall Pass tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallPassTab(vm: HallPassViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(vm) { vm.load() }
    val gate = state.gate

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (gate !is HpGate.Ready) {
                PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) { item { GateCard(gate, vm::load) } }
                }
            } else {
                ReviewList(vm, state)
            }
        }
    }
}

@Composable
private fun GateCard(gate: HpGate, onRetry: () -> Unit) {
    val warn = warnColor()
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if (gate is HpGate.Checking) Icons.Default.Search else Icons.Default.Lock, if (gate is HpGate.Checking) MaterialTheme.colorScheme.primary else warn, size = 44.dp)
                Text("Hall Pass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            when (gate) {
                HpGate.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Looking for the Hall Pass plugin…")
                }
                HpGate.NotInstalled -> Text(
                    "The Hall Pass plugin is not on this server. It finds which of your music is explicit and tags it, so playlists can leave it out. " +
                        "Install it from github.com/feerlessleadr/hall-pass-emby (or with the GitHub Repo Plugin Install plugin), restart Emby, then come back here.",
                )
                is HpGate.TooOld -> Text("Hall Pass ${gate.info.version} is too old for this tab: it needs ${HallPass.MIN_PLUGIN} or newer. Update the plugin from the Server tab.")
                is HpGate.Unreachable -> Text(gate.message)
                is HpGate.Ready -> Unit
            }
            if (gate !is HpGate.Checking) OutlinedButton(onClick = onRetry) { Text("Check again") }
        }
    }
}

/** A decision waiting for the person's yes: what it covers is spelled out in [message]. [artist] and [album] narrow it (null = any); both null means the whole view. */
private data class PendingBulk(val title: String, val message: String, val decision: String, val artist: String?, val album: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewList(vm: HallPassViewModel, state: HallPassState) {
    var pending by remember { mutableStateOf<PendingBulk?>(null) }
    var confirmScanAll by rememberSaveable { mutableStateOf(false) }
    val shown = when {
        state.grouped && state.mode == HpMode.Albums -> state.albums.size
        state.grouped -> state.artists.size
        else -> state.rows.size
    }
    val noun = when {
        state.grouped && state.mode == HpMode.Albums -> "albums"
        state.grouped -> "artists"
        else -> "tracks"
    }

    PullToRefreshBox(isRefreshing = false, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            item { SummaryCard(state.status, vm, onScanAll = { confirmScanAll = true }) }
            item { NextStepCard(state.status) }
            item { Filters(vm, state) }
            state.scope?.let { scope ->
                item {
                    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (scope.album == null) "${scope.artist.ifEmpty { "Unknown artist" }} (all albums)" else "${scope.album.ifEmpty { "No album" }} · ${scope.artist.ifEmpty { "unknown artist" }}",
                                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = vm::closeScope) { Text("Show all") }
                        }
                    }
                }
            }
            if (!state.grouped && state.total > 0) {
                item {
                    BulkCard(
                        count = state.total,
                        onSwipe = vm::startSwipe,
                        onMark = { decision ->
                            val word = if (decision == "explicit") "explicit" else "clean"
                            pending = PendingBulk(
                                title = "Mark all ${state.total} $word?",
                                message = "Every one of the ${state.total} tracks in this view (${describeView(state)}) will be marked $word, on every page, not only the ones on screen. " +
                                    "Nothing is written to your files until you apply, and you can clear a decision afterwards.",
                                decision = decision,
                                artist = state.scope?.artist,
                                album = state.scope?.album,
                            )
                        },
                    )
                }
            }
            when {
                state.grouped && state.mode == HpMode.Albums -> items(state.albums, key = { "a\u0000${it.artist}\u0000${it.album}" }) { album ->
                    AlbumCard(
                        album,
                        onOpen = { vm.openAlbum(album) },
                        onMark = { decision ->
                            val word = if (decision == "explicit") "explicit" else "clean"
                            pending = PendingBulk(
                                "Mark the whole album $word?",
                                "${album.matching} track(s) of \"${album.album.ifEmpty { "no album" }}\" by ${album.artist.ifEmpty { "unknown artist" }} will be marked $word. " +
                                    "Nothing is written to your files until you apply, and you can clear the decision afterwards.",
                                decision, album.artist, album.album,
                            )
                        },
                    )
                }
                state.grouped -> items(state.artists, key = { "r\u0000${it.artist}" }) { artist ->
                    ArtistCard(
                        artist,
                        onOpen = { vm.openArtist(artist) },
                        onMark = { decision ->
                            val word = if (decision == "explicit") "explicit" else "clean"
                            pending = PendingBulk(
                                "Mark everything by this artist $word?",
                                "${artist.matching} track(s) by ${artist.artist.ifEmpty { "unknown artist" }}, across ${artist.albums} ${if (artist.albums == 1) "album" else "albums"}, will be marked $word. " +
                                    "Nothing is written to your files until you apply, and you can clear the decisions afterwards.",
                                decision, artist.artist, null,
                            )
                        },
                    )
                }
                else -> items(state.rows, key = { it.id }) { row -> TrackCard(row, vm) }
            }
            item {
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                    shown == 0 -> Text(
                        if (state.status?.total == 0) "Nothing scanned yet. Press Scan now." else "Nothing here with these filters.",
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    shown < state.total -> {
                        LaunchedEffect(shown) { vm.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { TextButton(onClick = vm::loadMore) { Text("Show more ($shown of ${state.total})") } }
                    }
                    else -> Text("$shown $noun", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    pending?.let { p ->
        ConfirmDialog(
            title = p.title,
            message = p.message,
            confirm = if (p.decision == "explicit") "Mark explicit" else "Mark clean",
            onDismiss = { pending = null },
            onConfirm = { vm.decideMatching(p.decision, p.artist, p.album); pending = null },
        )
    }
    if (confirmScanAll) {
        ConfirmDialog(
            title = "Scan every track again?",
            message = "Hall Pass looks at the whole music library again, even tracks it already knows. This can take a while on a big library. Your manual decisions are kept.",
            confirm = "Scan all",
            onDismiss = { confirmScanAll = false },
            onConfirm = { vm.scan(true); confirmScanAll = false },
        )
    }
    state.preview?.let { preview -> ApplyDialog(preview, state.status, onDismiss = vm::clearPreview, onApply = vm::apply) }
    if (state.swipe) SwipeDialog(vm, state)
}

/** The current view in words, for the confirmation: the filter, the reason, the search and the opened album or artist. */
internal fun describeView(state: HallPassState): String {
    val parts = mutableListOf(state.filter.label)
    if (state.filter == HpFilter.Review && state.reason != HpReason.Any) parts += state.reason.label
    if (state.search.isNotBlank()) parts += "search \"${state.search.trim()}\""
    state.scope?.let { parts += if (it.album == null) it.artist.ifEmpty { "unknown artist" } else it.album.ifEmpty { "no album" } }
    return parts.joinToString(" · ")
}

@Composable
private fun BulkCard(count: Int, onSwipe: () -> Unit, onMark: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Decide faster", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Button(onClick = onSwipe, modifier = Modifier.fillMaxWidth()) { Text("Swipe through these $count") }
            Text("One at a time, swipe right for clean or left for explicit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Or decide all $count in this view at once:", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onMark("explicit") }) { Text("All $count explicit") }
                OutlinedButton(onClick = { onMark("clean") }) { Text("All $count clean") }
            }
        }
    }
}

/** The step the person is on and what to do about it, from what the plugin says has happened so far. Pure, so it is unit tested. */
internal data class NextStep(val number: Int, val text: String)

internal fun nextStep(status: HpStatus): NextStep {
    val review = status.count("Review")
    return when {
        status.scan.isRunning -> NextStep(1, "A scan is running. Wait for it to finish.")
        status.write.isRunning -> NextStep(5, "Tags are being written. Wait for it to finish.")
        status.total == 0 -> NextStep(1, "Tap Scan now. It only records what Hall Pass finds, so it is safe while dry run is on.")
        review > 0 -> NextStep(
            2,
            "$review tracks need your decision. Mark them Explicit or Clean below, swipe through them one at a time, or do a whole album or artist at once (Albums, Artists). Most played first puts what you actually listen to at the top. You can stop and come back, and you do not have to finish first: tracks left in review get the explicit-review tag when you apply.",
        )
        status.dryRun -> NextStep(
            3,
            "Tap Apply… to see what would change. Dry run is on, so to really write the tags switch it off first on the plugin's Emby page (Hall Pass > Settings > Tags & writing > Dry run), then come back and tap Apply.",
        )
        else -> NextStep(4, "Tap Apply… to see what would change, then write the tags. Every change can be undone from the plugin's History tab.")
    }
}

private val HOW_IT_WORKS = listOf(
    "Scan: tap Scan now. Safe with dry run on; it only records what Hall Pass finds.",
    "Review: mark tracks (or whole albums) Explicit or Clean. Your choices are kept and a scan never overwrites them. You do not have to finish.",
    "Preview: tap Apply… to see what would change.",
    "Turn dry run off on the plugin's Emby page (Hall Pass > Settings > Tags & writing). No need to scan again.",
    "Apply: tap Apply… again and confirm. Undo is in the plugin's History tab. Later changes only write the differences.",
)

@Composable
private fun NextStepCard(status: HpStatus?) {
    if (status == null) return
    var all by rememberSaveable { mutableStateOf(false) }
    val step = nextStep(status)
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What to do next", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Step ${step.number}: ${step.text}", style = MaterialTheme.typography.bodyMedium)
            if (all) {
                HOW_IT_WORKS.forEachIndexed { i, line ->
                    Text(
                        "${i + 1}. $line",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (i + 1 == step.number) FontWeight.Bold else FontWeight.Normal,
                        color = if (i + 1 == step.number) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { all = !all }) { Text(if (all) "Hide the steps" else "Show all the steps") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(status: HpStatus?, vm: HallPassViewModel, onScanAll: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hall Pass", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (status == null) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("${status.total} tracks looked at", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Explicit", "Review", "LikelyClean", "Clean", "Skipped").forEach { d ->
                    val n = status.count(d)
                    if (n > 0) Pill("${decisionLabel(d)} $n", decisionColor(d))
                }
            }
            if (status.dryRun) {
                Text("Dry run: scans only record decisions. Tags are written once dry run is switched off on the plugin's Emby page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status.scan.isRunning) {
                val fraction = if (status.scan.total > 0) status.scan.processed.toFloat() / status.scan.total else 0f
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
                Text("Scanning ${status.scan.processed} of ${status.scan.total}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::cancelScan) { Text("Stop scan") }
            } else if (status.write.isRunning) {
                val fraction = if (status.write.total > 0) status.write.processed.toFloat() / status.write.total else 0f
                LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
                Text(status.write.message.ifEmpty { "Writing tags…" }, style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.scan(false) }) { Text("Scan now") }
                    OutlinedButton(onClick = onScanAll) { Text("Scan all again") }
                    OutlinedButton(onClick = vm::loadPreview) { Text("Apply…") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Filters(vm: HallPassViewModel, state: HallPassState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HpFilter.entries.forEach { f ->
                FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
            }
        }
        if (state.filter == HpFilter.Review) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HpReason.entries.forEach { r ->
                    val n = if (r == HpReason.Any) state.status?.byDecision?.get("Review") else state.status?.reasonCount(r.key)
                    FilterChip(selected = state.reason == r, onClick = { vm.setReason(r) }, label = { Text(if (n != null) "${r.label} $n" else r.label) })
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HpMode.entries.forEach { m ->
                FilterChip(selected = state.mode == m, onClick = { vm.setMode(m) }, label = { Text(m.name) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Order:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HpSort.entries.forEach { o ->
                FilterChip(selected = state.sort == o, onClick = { vm.setSort(o) }, label = { Text(o.label) })
            }
        }
        OutlinedTextField(
            value = state.search,
            onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search artist, album or title") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        )
    }
}

@Composable
private fun TrackCard(row: HpRow, vm: HallPassViewModel) {
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { open = !open }, shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(row.artist, row.album).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (row.plays > 0) Text("played ${row.plays} ${if (row.plays == 1) "time" else "times"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(decisionLabel(row.decision), decisionColor(row.decision))
            }
            if (row.manual) Text("Your decision: ${row.override.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            else if (row.reason.isNotEmpty()) Text(row.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (open) {
                if (row.evidence.isEmpty()) Text(if (row.skipped) "In a skipped genre." else "No evidence.", style = MaterialTheme.typography.bodySmall)
                row.evidence.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.decide(row, "explicit") }, enabled = !(row.manual && row.override == "Explicit")) { Text("Explicit") }
                OutlinedButton(onClick = { vm.decide(row, "clean") }, enabled = !(row.manual && row.override == "Clean")) { Text("Clean") }
                if (row.manual) TextButton(onClick = { vm.decide(row, "clearOverride") }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: HpAlbum, onOpen: () -> Unit, onMark: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(album.album.ifEmpty { "(no album)" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist.ifEmpty { "(unknown artist)" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                (if (album.matching == album.total) "${album.total} tracks" else "${album.matching} of ${album.total} tracks") + " · " +
                    album.byDecision.entries.joinToString(", ") { "${decisionLabel(it.key)} ${it.value}" } + (if (album.plays > 0) " · played ${album.plays} times" else ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Open") }
                Button(onClick = { onMark("explicit") }) { Text("All explicit") }
                OutlinedButton(onClick = { onMark("clean") }) { Text("All clean") }
            }
        }
    }
}

@Composable
private fun ArtistCard(artist: HpArtist, onOpen: () -> Unit, onMark: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = HomeCardShape) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(artist.artist.ifEmpty { "(unknown artist)" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (artist.matching == artist.total) "${artist.total} tracks" else "${artist.matching} of ${artist.total} tracks") + " · " +
                    "${artist.albums} ${if (artist.albums == 1) "album" else "albums"}" + (if (artist.plays > 0) " · played ${artist.plays} times" else ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                artist.byDecision.entries.joinToString(", ") { "${decisionLabel(it.key)} ${it.value}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen) { Text("Open") }
                Button(onClick = { onMark("explicit") }) { Text("All explicit") }
                OutlinedButton(onClick = { onMark("clean") }) { Text("All clean") }
            }
        }
    }
}

@Composable
private fun ApplyDialog(preview: HpPreview, status: HpStatus?, onDismiss: () -> Unit, onApply: () -> Unit) {
    val dry = status?.dryRun == true
    ConfirmDialog(
        title = if (dry) "Dry run is on" else "Write the tags?",
        message = when {
            dry -> "${preview.wouldChange} item(s) would change, but dry run is on, so nothing can be written yet. When you are happy with your decisions: on the plugin's Emby page open Hall Pass > Settings > Tags & writing, untick Dry run and Save. Then come back and tap Apply… again. You do not need to scan again."
            preview.wouldChange == 0 -> "Every track already has the right tag."
            else -> "${preview.wouldChange} item(s) would change" + (if (preview.rollUps > 0) " (${preview.rollUps} albums or artists)" else "") + ": " +
                preview.byTag.entries.joinToString(", ") { "${it.value} to ${it.key}" } + ". Every change is journalled and can be undone from the plugin's History tab."
        },
        confirm = if (dry || preview.wouldChange == 0) "OK" else "Write tags",
        onDismiss = onDismiss,
        onConfirm = if (dry || preview.wouldChange == 0) onDismiss else onApply,
    )
}
